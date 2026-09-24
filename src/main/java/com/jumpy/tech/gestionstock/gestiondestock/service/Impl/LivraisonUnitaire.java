package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Envoi;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatEnvoi;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EnvoiRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.function.Consumer;

/**
 * Un envoi, une transaction.
 *
 * Sans cette separation, le premier courriel refuse emporterait dans son rollback le
 * compte-rendu de tous les autres du meme paquet : les reussis seraient renvoyes au passage
 * suivant, et le client recevrait sa facture plusieurs fois.
 *
 * Classe a part parce qu'un appel a `this` ne traverse pas le proxy de Spring : la propagation y
 * serait ignoree, et le defaut reviendrait sans rien changer d'apparent.
 */
@Service
@Slf4j
public class LivraisonUnitaire {

    private final EnvoiRepository envoiRepository;

    public LivraisonUnitaire(EnvoiRepository envoiRepository) {
        this.envoiRepository = envoiRepository;
    }

    /** Rend vrai si l'envoi est parti. Ne propage jamais : un echec se note, il n'interrompt pas. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean livrer(Long idEnvoi, Consumer<Envoi> livraison) {
        Envoi envoi = envoiRepository.findById(idEnvoi).orElse(null);
        if (envoi == null || envoi.getEtat() != EtatEnvoi.A_ENVOYER) {
            return false;
        }
        try {
            livraison.accept(envoi);
            envoi.reussi(Instant.now());
            if (envoi.isSensible()) {
                // Le courriel d'inscription porte un mot de passe provisoire. La ligne reste, avec
                // sa destination et sa date — c'est ce qui permet de savoir qui a recu quoi — mais
                // le texte s'en va : sans cela, le mot de passe resterait lisible en base
                // longtemps apres que le gerant l'a change.
                envoi.setCorps("(contenu effacé après envoi)");
            }
            envoiRepository.save(envoi);
            return true;
        } catch (RuntimeException echec) {
            envoi.echoue(Instant.now(), echec.getMessage());
            envoiRepository.save(envoi);
            if (envoi.getEtat() == EtatEnvoi.ABANDONNE) {
                log.error("Envoi {} abandonne apres {} tentatives : {}",
                        idEnvoi, envoi.getTentatives(), echec.getMessage());
            } else {
                log.warn("Envoi {} en echec (tentative {}), reprise a {} : {}",
                        idEnvoi, envoi.getTentatives(), envoi.getProchaineTentative(),
                        echec.getMessage());
            }
            return false;
        }
    }
}
