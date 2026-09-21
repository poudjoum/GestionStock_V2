package com.jumpy.tech.gestionstock.gestiondestock.config.security.jwt;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.entities.JetonRafraichissement;
import com.jumpy.tech.gestionstock.gestiondestock.entities.MotifRevocation;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Utilisateur;
import com.jumpy.tech.gestionstock.gestiondestock.repository.JetonRafraichissementRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.UtilisateurRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * La vie des jetons de rafraichissement : en creer un, l'echanger, le revoquer.
 *
 * Chaque echange remplace le jeton par un nouveau et revoque l'ancien. C'est ce qui permet de
 * s'apercevoir d'un vol : si l'ancien revient, c'est qu'il a ete copie — celui qui le presente
 * n'est pas seul a l'avoir, et tout le compte est alors ferme.
 */
@Service
@Slf4j
public class ServiceDeRafraichissement {

    private static final SecureRandom ALEA = new SecureRandom();

    private final JetonRafraichissementRepository repository;
    private final UtilisateurRepository utilisateurRepository;
    private final RevocationImmediate revocationImmediate;
    private final long dureeMs;

    public ServiceDeRafraichissement(JetonRafraichissementRepository repository,
                                     UtilisateurRepository utilisateurRepository,
                                     RevocationImmediate revocationImmediate,
                                     @Value("${app.jwtRefreshExpirationMS}") long dureeMs) {
        this.repository = repository;
        this.utilisateurRepository = utilisateurRepository;
        this.revocationImmediate = revocationImmediate;
        this.dureeMs = dureeMs;
    }

    /**
     * Ce que rend un echange : le nouveau jeton, et de quoi signer le jeton d'acces qui va avec.
     *
     * Les deux ensemble, parce que les roles sont en LAZY : les lire depuis le controleur, hors
     * transaction et avec `open-in-view: false`, partirait en LazyInitializationException.
     */
    public record Rafraichi(String jeton, UserDetailsImpl details) {
    }

    @Transactional
    public String creer(Long idUtilisateur) {
        Utilisateur utilisateur = utilisateurRepository.getReferenceById(idUtilisateur);
        JetonRafraichissement jeton = new JetonRafraichissement();
        jeton.setJeton(nouveauJeton());
        jeton.setUtilisateur(utilisateur);
        jeton.setExpiration(Instant.now().plusMillis(dureeMs));
        return repository.save(jeton).getJeton();
    }

    /**
     * Echange un jeton contre un nouveau, et rend de quoi refabriquer un jeton d'acces.
     *
     * Un jeton deja revoque qui revient n'est pas une etourderie : c'est une copie qui circule. On
     * ferme alors tout ce qui reste ouvert pour ce compte plutot que de refuser cette seule
     * requete — le voleur et le titulaire se reconnecteront, et le titulaire s'en apercevra.
     */
    @Transactional
    public Rafraichi echanger(String valeur) {
        Instant maintenant = Instant.now();
        JetonRafraichissement presente = repository.findByJeton(valeur)
                .orElseThrow(() -> new BadCredentialsException("Jeton de rafraîchissement inconnu"));
        Utilisateur titulaire = presente.getUtilisateur();

        // Un jeton revoque qui revient : ce qu'on en fait depend de la raison de sa revocation.
        //
        // Remplace par rotation, il ne devait jamais revenir — le client qui l'a echange en a recu
        // un autre. S'il revient, une copie circule, et tout le compte se ferme. La fermeture
        // passe par `revocationImmediate` : ecrite ici, le refus qui la suit l'annulerait en
        // repliant la transaction, et le voleur repartirait avec des jetons valides.
        //
        // Deconnecte, en revanche, c'est une maladresse de client : un onglet reste ouvert, une
        // requete differee qui repart. Fermer tout le compte pour cela couperait la caisse restee
        // ouverte au comptoir, sans le moindre voleur.
        if (presente.getRevoqueLe() != null) {
            if (presente.getMotifRevocation() == MotifRevocation.ROTATION) {
                int fermes = revocationImmediate.fermerTout(titulaire.getId(), maintenant);
                log.warn("Jeton de rafraichissement deja echange represente pour le compte {} : "
                        + "{} jeton(s) ferme(s) par precaution", titulaire.getId(), fermes);
            }
            throw new BadCredentialsException("Jeton de rafraîchissement déjà utilisé");
        }
        if (!presente.getExpiration().isAfter(maintenant)) {
            throw new BadCredentialsException("Jeton de rafraîchissement expiré");
        }
        // Un compte ferme entre-temps ne redemande pas de jeton : c'est precisement ce qu'un JWT
        // de 24 h ne savait pas faire.
        if (!titulaire.isActif()) {
            revocationImmediate.fermerTout(titulaire.getId(), maintenant);
            throw new DisabledException("Ce compte est fermé");
        }

        presente.revoquer(maintenant, MotifRevocation.ROTATION);
        repository.save(presente);

        JetonRafraichissement nouveau = new JetonRafraichissement();
        nouveau.setJeton(nouveauJeton());
        nouveau.setUtilisateur(titulaire);
        nouveau.setExpiration(maintenant.plusMillis(dureeMs));
        repository.save(nouveau);

        return new Rafraichi(nouveau.getJeton(), UserDetailsImpl.build(titulaire));
    }

    /** Deconnexion : le jeton presente cesse de valoir, les autres appareils restent connectes. */
    @Transactional
    public void revoquer(String valeur) {
        repository.findByJeton(valeur).ifPresent(jeton -> {
            if (jeton.getRevoqueLe() == null) {
                jeton.revoquer(Instant.now(), MotifRevocation.DECONNEXION);
                repository.save(jeton);
            }
        });
    }

    /**
     * Ferme tout ce qui reste ouvert pour un compte : acces retire, mot de passe change.
     *
     * Dans la transaction de l'appelant, volontairement : si la fermeture du compte echoue, la
     * revocation doit tomber avec elle. C'est l'inverse du cas du jeton copie, ou la revocation
     * doit survivre au refus.
     */
    @Transactional
    public void revoquerTout(Long idUtilisateur) {
        int fermes = repository.revoquerTousPourUtilisateur(idUtilisateur, Instant.now());
        if (fermes > 0) {
            log.info("Compte {} : {} jeton(s) de rafraichissement revoque(s)", idUtilisateur, fermes);
        }
    }

    /** 256 bits tires au hasard, en base64 sans remplissage : 43 caracteres, sous la limite de 64. */
    private static String nouveauJeton() {
        byte[] octets = new byte[32];
        ALEA.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }
}
