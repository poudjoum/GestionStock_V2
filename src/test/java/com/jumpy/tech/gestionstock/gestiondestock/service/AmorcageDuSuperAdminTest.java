package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.AmorcageDuSuperAdmin;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Utilisateur;
import com.jumpy.tech.gestionstock.gestiondestock.repository.RoleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.UtilisateurRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L'amorcage du super-administrateur par l'environnement.
 *
 * `@Transactional` sur la classe pour deux raisons. Les roles sont charges en LAZY : les lire
 * apres coup, hors session, leve une LazyInitializationException que la production ne connait pas
 * — elle les lit dans la transaction de l'amorcage. Et Spring annule la transaction a la fin de
 * chaque methode : les comptes crees ici ne restent pas dans la base que toute la campagne
 * partage.
 *
 * Le premier compte d'une installation se creait par la route d'inscription, ouverte tant que la
 * base etait vide. Cela ne marche plus sur un serveur deja en service dont personne ne connait le
 * mot de passe, ni sur une installation qu'on veut monter sans ouvrir un navigateur.
 *
 * Le composant est construit a la main plutot que pris dans le contexte : ses trois valeurs
 * viennent de la configuration, et les faire varier autrement obligerait a relancer une
 * application par cas de figure.
 */
@Transactional
class AmorcageDuSuperAdminTest extends AbstractIntegrationTest {

    /** Douze caracteres au moins : c'est ce que le composant exige. */
    private static final String MOT_DE_PASSE = "UnMotDePasseAssezLong!2026";

    @Autowired
    private UtilisateurRepository utilisateurRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder encodeur;

    private AmorcageDuSuperAdmin amorcage(String username, String email, String motdepasse) {
        return new AmorcageDuSuperAdmin(utilisateurRepository, roleRepository, encodeur,
                username, email, motdepasse);
    }

    private String nomUnique() {
        return "editeur-" + UUID.randomUUID();
    }

    @Test
    void le_compte_est_cree_avec_le_rang_au_dessus_des_entreprises() {
        String username = nomUnique();

        amorcage(username, username + "@exemple.test", MOT_DE_PASSE).amorcer();

        Utilisateur cree = utilisateurRepository.findUtilisateurByUsername(username).orElseThrow();
        assertThat(cree.getRoles()).anyMatch(r -> r.getRoleName() == ERole.ROLE_SUPER_ADMIN);
        // Aucune entreprise, et c'est le point : il est au-dessus d'elles, c'est lui qui les cree.
        assertThat(cree.getEntreprise()).isNull();
        assertThat(cree.isActif()).isTrue();
    }

    @Test
    void le_mot_de_passe_est_encode_et_jamais_stocke_en_clair() {
        String username = nomUnique();

        amorcage(username, username + "@exemple.test", MOT_DE_PASSE).amorcer();

        Utilisateur cree = utilisateurRepository.findUtilisateurByUsername(username).orElseThrow();
        assertThat(cree.getMotdepasse()).isNotEqualTo(MOT_DE_PASSE);
        assertThat(encodeur.matches(MOT_DE_PASSE, cree.getMotdepasse())).isTrue();
    }

    @Test
    void rejouer_l_amorcage_ne_touche_pas_au_compte() {
        String username = nomUnique();
        amorcage(username, username + "@exemple.test", MOT_DE_PASSE).amorcer();
        String empreinteDOrigine =
                utilisateurRepository.findUtilisateurByUsername(username).orElseThrow().getMotdepasse();

        // Le serveur redemarre : le composant repasse, avec un autre mot de passe dans
        // l'environnement. Il ne doit rien reecrire — sinon un mot de passe change dans
        // l'application serait defait au redemarrage suivant, sans que personne ne comprenne.
        amorcage(username, username + "@exemple.test", "UnAutreMotDePasse!2026").amorcer();

        Utilisateur relu = utilisateurRepository.findUtilisateurByUsername(username).orElseThrow();
        assertThat(relu.getMotdepasse()).isEqualTo(empreinteDOrigine);
        assertThat(utilisateurRepository.findAllBy().stream()
                .filter(u -> username.equals(u.getUsername()))).hasSize(1);
    }

    @Test
    void un_compte_existant_sans_le_rang_le_recoit() {
        String username = nomUnique();
        Utilisateur ordinaire = new Utilisateur(username, username + "@exemple.test",
                encodeur.encode(MOT_DE_PASSE));
        utilisateurRepository.save(ordinaire);

        amorcage(username, username + "@exemple.test", MOT_DE_PASSE).amorcer();

        assertThat(utilisateurRepository.findUtilisateurByUsername(username).orElseThrow().getRoles())
                .anyMatch(r -> r.getRoleName() == ERole.ROLE_SUPER_ADMIN);
    }

    @Test
    void trois_variables_vides_ne_creent_rien() {
        long avant = utilisateurRepository.count();

        amorcage("", "", "").amorcer();

        assertThat(utilisateurRepository.count()).isEqualTo(avant);
    }

    @Test
    void une_variable_manquante_ne_cree_rien() {
        String username = nomUnique();
        long avant = utilisateurRepository.count();

        // Un `.env` a moitie rempli est une erreur de saisie, pas une demande a moitie honoree.
        amorcage(username, "", MOT_DE_PASSE).amorcer();

        assertThat(utilisateurRepository.count()).isEqualTo(avant);
        assertThat(utilisateurRepository.findUtilisateurByUsername(username)).isEmpty();
    }

    @Test
    void un_mot_de_passe_trop_court_est_refuse() {
        String username = nomUnique();

        // Ce compte peut tout faire, sur toutes les entreprises : il ne se cree pas avec un mot
        // de passe tape a la hate pour essayer.
        amorcage(username, username + "@exemple.test", "court").amorcer();

        assertThat(utilisateurRepository.findUtilisateurByUsername(username)).isEmpty();
    }

    @Test
    void une_adresse_deja_prise_par_un_autre_compte_est_refusee() {
        String occupant = nomUnique();
        String adresse = occupant + "@exemple.test";
        utilisateurRepository.save(new Utilisateur(occupant, adresse, encodeur.encode(MOT_DE_PASSE)));

        String nouveau = nomUnique();
        // La contrainte d'unicite ferait echouer l'enregistrement, et l'echec arriverait au
        // demarrage sous une forme que personne ne saurait lire.
        amorcage(nouveau, adresse, MOT_DE_PASSE).amorcer();

        assertThat(utilisateurRepository.findUtilisateurByUsername(nouveau)).isEmpty();
    }
}
