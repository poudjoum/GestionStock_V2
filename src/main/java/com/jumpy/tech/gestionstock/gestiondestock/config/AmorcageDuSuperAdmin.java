package com.jumpy.tech.gestionstock.gestiondestock.config;

import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Role;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Utilisateur;
import com.jumpy.tech.gestionstock.gestiondestock.repository.RoleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.UtilisateurRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Cree le super-administrateur decrit par l'environnement, s'il n'existe pas deja.
 *
 * Le premier compte d'une installation se creait jusqu'ici par la route d'inscription, ouverte
 * tant que la base etait vide. Cela marche une fois, sur une machine qu'on a sous la main. Cela ne
 * marche plus du tout sur un serveur deja en service dont personne ne connait plus le mot de
 * passe, ni sur une installation neuve qu'on veut monter sans ouvrir un navigateur.
 *
 * Trois variables suffisent donc, lues au demarrage. Elles vivent dans le `.env` du serveur, avec
 * le mot de passe de la base et le secret JWT : le meme fichier, les memes droits, la meme
 * discretion. Rien de ce qu'elles contiennent n'est ecrit dans le journal, mot de passe compris.
 *
 * <b>Ce composant ne modifie jamais un compte existant.</b> Il en cree un, ou il ne fait rien.
 * S'il reecrivait le mot de passe a chaque demarrage, l'environnement deviendrait la seule verite
 * et un changement de mot de passe fait dans l'application serait defait au prochain
 * redemarrage — sans que personne ne comprenne pourquoi. La seule exception est le rang : un
 * compte qui porte deja ce nom mais pas ROLE_SUPER_ADMIN le recoit, parce que c'est precisement
 * ce que l'on demandait.
 */
@Component
@Slf4j
public class AmorcageDuSuperAdmin implements ApplicationRunner {

    /**
     * La longueur minimale du mot de passe d'amorcage.
     *
     * Ce compte peut tout faire, sur toutes les entreprises, et son identifiant est connu de qui
     * lit le `.env`. Douze caracteres ne sont pas une politique de securite — c'est le garde-fou
     * qui empeche de creer ce compte-la avec un mot de passe tape a la hate pour « essayer ».
     */
    private static final int LONGUEUR_MINIMALE = 12;

    private final UtilisateurRepository utilisateurRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder encodeur;
    private final String username;
    private final String email;
    private final String motdepasse;

    public AmorcageDuSuperAdmin(UtilisateurRepository utilisateurRepository,
                                RoleRepository roleRepository,
                                PasswordEncoder encodeur,
                                @Value("${amorcage.super-admin.username:}") String username,
                                @Value("${amorcage.super-admin.email:}") String email,
                                @Value("${amorcage.super-admin.motdepasse:}") String motdepasse) {
        this.utilisateurRepository = utilisateurRepository;
        this.roleRepository = roleRepository;
        this.encodeur = encodeur;
        this.username = username;
        this.email = email;
        this.motdepasse = motdepasse;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        amorcer();
    }

    /**
     * Le travail lui-meme, separe du demarrage pour qu'il se teste sans relancer l'application.
     *
     * Il ne leve jamais : un amorcage impossible ne doit pas empecher le serveur de demarrer. Une
     * caisse qui refuse d'ouvrir parce qu'un compte d'administration n'a pas pu etre cree serait
     * un remede pire que le mal — le journal le dit, et l'application sert.
     */
    @Transactional
    public void amorcer() {
        if (!StringUtils.hasText(username) && !StringUtils.hasText(email)
                && !StringUtils.hasText(motdepasse)) {
            // Rien n'est demande : c'est le cas ordinaire d'une installation deja amorcee.
            return;
        }
        if (!StringUtils.hasText(username) || !StringUtils.hasText(email)
                || !StringUtils.hasText(motdepasse)) {
            log.warn("Amorçage du super-administrateur incomplet : il faut SUPER_ADMIN_USERNAME, "
                    + "SUPER_ADMIN_EMAIL et SUPER_ADMIN_MOTDEPASSE. Aucun compte n'est créé.");
            return;
        }
        if (motdepasse.length() < LONGUEUR_MINIMALE) {
            log.error("Amorçage du super-administrateur refusé : le mot de passe fait moins de {} "
                    + "caractères. Ce compte peut tout faire, sur toutes les entreprises.",
                    LONGUEUR_MINIMALE);
            return;
        }

        Optional<Utilisateur> existant = utilisateurRepository.findUtilisateurByUsername(username);
        if (existant.isPresent()) {
            promouvoirSiNecessaire(existant.get());
            return;
        }
        // L'adresse est unique en base : la contrainte ferait echouer l'enregistrement, et l'echec
        // arriverait au demarrage sous une forme que personne ne saurait lire.
        if (utilisateurRepository.existsByEmail(email)) {
            log.error("Amorçage du super-administrateur refusé : l'adresse de courriel demandée "
                    + "appartient déjà à un autre compte. Aucun compte n'est créé.");
            return;
        }

        Optional<Role> rang = roleRepository.findByRoleName(ERole.ROLE_SUPER_ADMIN);
        if (rang.isEmpty()) {
            log.error("Amorçage du super-administrateur impossible : le rôle ROLE_SUPER_ADMIN est "
                    + "absent de la base, la migration V10 n'a pas été appliquée.");
            return;
        }

        Utilisateur compte = new Utilisateur(username, email, encodeur.encode(motdepasse));
        // Aucune entreprise, et c'est le point : le super-administrateur est au-dessus d'elles,
        // c'est lui qui les cree. Un compte rattache ne verrait que la sienne.
        compte.setEntreprise(null);
        compte.getRoles().add(rang.get());
        utilisateurRepository.save(compte);

        // L'identifiant, jamais le mot de passe. Un journal se lit, se copie et s'archive.
        log.info("Super-administrateur « {} » créé par amorçage.", username);
    }

    private void promouvoirSiNecessaire(Utilisateur compte) {
        boolean deja = compte.getRoles().stream()
                .anyMatch(role -> role.getRoleName() == ERole.ROLE_SUPER_ADMIN);
        if (deja) {
            // Le cas de tous les demarrages suivants : le compte est la, rien a faire. On ne
            // reecrit pas son mot de passe — ce serait defaire en silence celui qu'on aurait
            // change dans l'application.
            log.debug("Super-administrateur « {} » déjà en place.", username);
            return;
        }
        roleRepository.findByRoleName(ERole.ROLE_SUPER_ADMIN).ifPresent(rang -> {
            compte.getRoles().add(rang);
            utilisateurRepository.save(compte);
            log.info("Le compte « {} » existait sans le rang de super-administrateur : il le reçoit.",
                    username);
        });
    }
}
