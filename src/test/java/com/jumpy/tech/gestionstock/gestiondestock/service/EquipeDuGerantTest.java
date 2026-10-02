package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.NouveauCollaborateurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.UserDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Utilisateur;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.UtilisateurRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le gerant ajoute ses collaborateurs.
 *
 * Il n'y avait aucun moyen de le faire : la creation d'un compte etait reservee a l'administrateur,
 * et exigeait une date de naissance et une adresse complete qu'on ne demande pas a un caissier.
 */
class EquipeDuGerantTest extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired
    private EntrepriseService entrepriseService;
    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private Long idEntreprise;

    @BeforeEach
    void unMagasin() {
        idEntreprise = entrepriseService.save(EntrepriseDto.builder()
                .nom("Quincaillerie " + UUID.randomUUID())
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .build()).getId();
    }

    @AfterEach
    void oublierLUtilisateur() {
        SecurityContextHolder.clearContext();
    }

    private void connecte(ERole role) {
        UserDetailsImpl principal = new UserDetailsImpl(1L, "chef-" + UUID.randomUUID(), "chef@exemple.test",
                "x", idEntreprise, List.of(new SimpleGrantedAuthority(role.name())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private NouveauCollaborateurDto caissier(ERole... roles) {
        return NouveauCollaborateurDto.builder()
                .nom("Ngo Bassa")
                .prenoms("Aline")
                .username("aline-" + UUID.randomUUID())
                .numTel("677112233")
                .motDePasse("Provisoire2026")
                .roles(List.of(roles))
                .build();
    }

    @Test
    @Transactional
    void le_gerant_ajoute_un_caissier_sans_date_de_naissance_ni_adresse() {
        connecte(ERole.ROLE_MANAGER);

        UserDto compte = userService.ajouterCollaborateur(caissier(ERole.ROLE_CAISSIER));

        Utilisateur enBase = utilisateurRepository.findById(compte.getId()).orElseThrow();
        assertThat(enBase.getEntreprise().getId()).isEqualTo(idEntreprise);
        assertThat(enBase.isActif()).isTrue();
        // Le mot de passe est provisoire : la premiere connexion en demandera un autre.
        assertThat(enBase.isMotdepasseAChanger()).isTrue();
        assertThat(enBase.getRoles()).extracting(r -> r.getRoleName()).containsExactly(ERole.ROLE_CAISSIER);
    }

    @Test
    void le_gerant_ne_nomme_ni_administrateur_ni_gerant() {
        connecte(ERole.ROLE_MANAGER);

        assertThatThrownBy(() -> userService.ajouterCollaborateur(caissier(ERole.ROLE_ADMIN)))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("administrateur");
        assertThatThrownBy(() -> userService.ajouterCollaborateur(caissier(ERole.ROLE_MANAGER)))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("gérant");
    }

    @Test
    void le_gerant_ne_touche_pas_au_compte_de_l_administrateur() {
        connecte(ERole.ROLE_ADMIN);
        UserDto administrateur = userService.ajouterCollaborateur(caissier(ERole.ROLE_ADMIN));

        connecte(ERole.ROLE_MANAGER);

        assertThatThrownBy(() -> userService.changerActivation(administrateur.getId(), false))
                .isInstanceOf(InvalidEntityException.class);
        assertThatThrownBy(() -> userService.reinitialiserMotDePasse(administrateur.getId(), "Nouveau2026!"))
                .isInstanceOf(InvalidEntityException.class);
    }

    @Test
    void le_gerant_gere_son_equipe() {
        connecte(ERole.ROLE_MANAGER);
        UserDto compte = userService.ajouterCollaborateur(caissier(ERole.ROLE_CAISSIER));

        userService.changerRoles(compte.getId(), List.of(ERole.ROLE_CAISSIER, ERole.ROLE_MAGASINIER));
        userService.changerActivation(compte.getId(), false);

        assertThat(utilisateurRepository.findById(compte.getId()).orElseThrow().isActif()).isFalse();
        assertThat(userService.rolesAttribuables())
                .containsExactlyInAnyOrder(ERole.ROLE_CAISSIER, ERole.ROLE_MAGASINIER, ERole.ROLE_COMPTABLE);
    }

    @Test
    void un_identifiant_deja_pris_est_refuse_lisiblement() {
        connecte(ERole.ROLE_MANAGER);
        NouveauCollaborateurDto premier = caissier(ERole.ROLE_CAISSIER);
        userService.ajouterCollaborateur(premier);

        NouveauCollaborateurDto second = caissier(ERole.ROLE_CAISSIER);
        second.setUsername(premier.getUsername());

        assertThatThrownBy(() -> userService.ajouterCollaborateur(second))
                .hasMessageContaining("identifiant est déjà pris");
    }

    @Test
    void les_champs_manquants_sont_tous_dits_d_un_coup() {
        connecte(ERole.ROLE_MANAGER);

        assertThatThrownBy(() -> userService.ajouterCollaborateur(new NouveauCollaborateurDto()))
                .isInstanceOfSatisfying(InvalidEntityException.class, e -> assertThat(e.getErrors())
                        .contains("Veuillez renseigner le nom du collaborateur",
                                "Veuillez choisir un identifiant de connexion",
                                "Veuillez choisir au moins un rôle"));
    }
}
