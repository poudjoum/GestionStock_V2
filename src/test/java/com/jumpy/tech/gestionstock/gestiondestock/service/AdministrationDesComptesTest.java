package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.AdresseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.UserDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.UtilisateurRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * L'administration des comptes, cloisonnee comme le reste.
 *
 * `/users` rendait tous les comptes de toutes les entreprises, un compte ne se rattachait a une
 * entreprise qu'en modifiant la base a la main, et rien ne permettait de changer un role, de
 * fermer un acces ni de reinitialiser un mot de passe.
 */
class AdministrationDesComptesTest extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired
    private EntrepriseService entrepriseService;
    @Autowired
    private UtilisateurRepository utilisateurRepository;
    @Autowired
    private PasswordEncoder encodeur;

    private Long idEntrepriseA;
    private Long idEntrepriseB;

    @BeforeEach
    void deuxEntreprises() {
        idEntrepriseA = creerEntreprise();
        idEntrepriseB = creerEntreprise();
    }

    @AfterEach
    void oublierLUtilisateur() {
        SecurityContextHolder.clearContext();
    }

    private Long creerEntreprise() {
        return entrepriseService.save(EntrepriseDto.builder()
                .nom("Entreprise " + UUID.randomUUID())
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .build()).getId();
    }

    private void connecte(String username, Long idEntreprise, ERole role) {
        UserDetailsImpl principal = new UserDetailsImpl(1L, username, username + "@exemple.test",
                "x", idEntreprise, List.of(new SimpleGrantedAuthority(role.name())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private UserDto creerCompte(String nom) {
        return userService.save(UserDto.builder()
                .nom(nom)
                .prenoms("Employe")
                .username("u-" + UUID.randomUUID())
                .email(UUID.randomUUID() + "@exemple.test")
                .motdepasse("MotDePasse123!")
                .numTel("690000000")
                .dateNaissance(Instant.parse("1990-01-01T00:00:00Z"))
                .adresse(AdresseDto.builder().adresse1("Rue 1").ville("Douala").pays("Cameroun").build())
                .build());
    }

    @Test
    void un_compte_cree_rejoint_l_entreprise_de_son_createur() {
        connecte("adminA", idEntrepriseA, ERole.ROLE_ADMIN);

        UserDto compte = creerCompte("Employe de A");

        assertThat(compte.getEntreprise()).isNotNull();
        assertThat(compte.getEntreprise().getId()).isEqualTo(idEntrepriseA);
    }

    @Test
    void le_mot_de_passe_n_est_jamais_rendu_et_se_trouve_chiffre_en_base() {
        connecte("adminA", idEntrepriseA, ERole.ROLE_ADMIN);

        UserDto compte = creerCompte("Employe de A");

        // fromEntity recopiait l'empreinte BCrypt dans le DTO : la liste des comptes la rendait
        // pour chacun, offerte a qui voulait l'attaquer hors ligne.
        assertThat(compte.getMotdepasse()).isNull();
        String enBase = utilisateurRepository.findById(compte.getId()).orElseThrow().getMotdepasse();
        assertThat(enBase).isNotEqualTo("MotDePasse123!");
        assertThat(encodeur.matches("MotDePasse123!", enBase)).isTrue();
    }

    @Test
    void un_administrateur_ne_voit_que_les_comptes_de_son_entreprise() {
        connecte("adminA", idEntrepriseA, ERole.ROLE_ADMIN);
        creerCompte("Employe de A");
        connecte("adminB", idEntrepriseB, ERole.ROLE_ADMIN);
        creerCompte("Employe de B");

        assertThat(userService.findAll())
                .extracting(UserDto::getNom)
                .containsOnly("Employe de B");
    }

    @Test
    void lire_le_compte_d_une_autre_entreprise_rend_un_404() {
        connecte("adminA", idEntrepriseA, ERole.ROLE_ADMIN);
        Long idChezA = creerCompte("Employe de A").getId();

        connecte("adminB", idEntrepriseB, ERole.ROLE_ADMIN);
        assertThatThrownBy(() -> userService.findById(idChezA))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void les_roles_se_remplacent() {
        connecte("adminA", idEntrepriseA, ERole.ROLE_ADMIN);
        Long id = creerCompte("Employe de A").getId();

        UserDto avecRoles = userService.changerRoles(id, List.of(ERole.ROLE_CAISSIER, ERole.ROLE_COMPTABLE));
        assertThat(avecRoles.getRoles()).hasSize(2);

        // Remplace, et n'ajoute pas : c'est ce qui permet de retirer un role.
        UserDto reduit = userService.changerRoles(id, List.of(ERole.ROLE_CAISSIER));
        assertThat(reduit.getRoles()).hasSize(1);
    }

    @Test
    void un_administrateur_ne_s_accorde_pas_le_rang_de_super_administrateur() {
        connecte("adminA", idEntrepriseA, ERole.ROLE_ADMIN);
        Long id = creerCompte("Employe de A").getId();

        assertThatThrownBy(() -> userService.changerRoles(id, List.of(ERole.ROLE_SUPER_ADMIN)))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("super-administrateur");
    }

    @Test
    void un_compte_se_ferme_sans_etre_supprime() {
        connecte("adminA", idEntrepriseA, ERole.ROLE_ADMIN);
        Long id = creerCompte("Employe de A").getId();

        UserDto ferme = userService.changerActivation(id, false);

        assertThat(ferme.isActif()).isFalse();
        // Il reste en base : l'employe parti demeure l'auteur de ce qu'il a saisi.
        assertThat(utilisateurRepository.findById(id)).isPresent();
    }

    @Test
    void on_ne_ferme_pas_son_propre_acces() {
        connecte("adminA", idEntrepriseA, ERole.ROLE_ADMIN);
        UserDto moi = creerCompte("Administrateur de A");
        connecte(moi.getUsername(), idEntrepriseA, ERole.ROLE_ADMIN);

        // Sinon une entreprise se retrouverait sans personne pour rouvrir.
        assertThatThrownBy(() -> userService.changerActivation(moi.getId(), false))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("propre accès");
    }

    @Test
    void seul_le_super_administrateur_rattache_un_compte_a_une_entreprise() {
        connecte("adminA", idEntrepriseA, ERole.ROLE_ADMIN);
        Long id = creerCompte("Employe de A").getId();

        assertThatThrownBy(() -> userService.rattacherAEntreprise(id, idEntrepriseB))
                .isInstanceOf(InvalidEntityException.class);

        connecte("editeur", null, ERole.ROLE_SUPER_ADMIN);
        UserDto deplace = userService.rattacherAEntreprise(id, idEntrepriseB);
        assertThat(deplace.getEntreprise().getId()).isEqualTo(idEntrepriseB);
    }

    @Test
    void un_administrateur_reinitialise_un_mot_de_passe_sans_connaitre_l_ancien() {
        connecte("adminA", idEntrepriseA, ERole.ROLE_ADMIN);
        Long id = creerCompte("Employe de A").getId();

        userService.reinitialiserMotDePasse(id, "NouveauSecret1");

        String enBase = utilisateurRepository.findById(id).orElseThrow().getMotdepasse();
        assertThat(encodeur.matches("NouveauSecret1", enBase)).isTrue();
    }

    @Test
    void changer_son_mot_de_passe_exige_l_ancien() {
        connecte("adminA", idEntrepriseA, ERole.ROLE_ADMIN);
        UserDto moi = creerCompte("Titulaire");
        connecte(moi.getUsername(), idEntrepriseA, ERole.ROLE_ADMIN);

        assertThatThrownBy(() -> userService.changerSonMotDePasse("le-mauvais", "NouveauSecret1"))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("ancien mot de passe");

        userService.changerSonMotDePasse("MotDePasse123!", "NouveauSecret1");
        String enBase = utilisateurRepository.findById(moi.getId()).orElseThrow().getMotdepasse();
        assertThat(encodeur.matches("NouveauSecret1", enBase)).isTrue();
    }

    @Test
    void un_mot_de_passe_trop_court_est_refuse() {
        connecte("adminA", idEntrepriseA, ERole.ROLE_ADMIN);
        Long id = creerCompte("Employe de A").getId();

        assertThatThrownBy(() -> userService.reinitialiserMotDePasse(id, "court"))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("8 caractères");
    }
}
