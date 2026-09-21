package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.AdresseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.InscriptionEntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.UserDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Utilisateur;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.UtilisateurRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
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
 * L'inscription d'une entreprise avec son premier administrateur.
 *
 * Les deux allaient deja ensemble sans que rien ne les lie : il fallait creer l'entreprise, creer
 * un compte, puis les rattacher — et cette derniere etape n'etait possible qu'en modifiant la base.
 */
class InscriptionDEntrepriseTest extends AbstractIntegrationTest {

    @Autowired
    private EntrepriseService entrepriseService;
    @Autowired
    private UtilisateurRepository utilisateurRepository;
    @Autowired
    private PasswordEncoder encodeur;

    @AfterEach
    void oublierLUtilisateur() {
        SecurityContextHolder.clearContext();
    }

    private void connecte(ERole role) {
        UserDetailsImpl principal = new UserDetailsImpl(1L, "acteur", "acteur@exemple.test",
                "x", null, List.of(new SimpleGrantedAuthority(role.name())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private InscriptionEntrepriseDto inscription() {
        InscriptionEntrepriseDto inscription = new InscriptionEntrepriseDto();
        inscription.setEntreprise(EntrepriseDto.builder()
                .nom("Entreprise " + UUID.randomUUID())
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .build());
        inscription.setAdministrateur(UserDto.builder()
                .nom("Gerant")
                .prenoms("Premier")
                .username("gerant-" + UUID.randomUUID())
                .email(UUID.randomUUID() + "@exemple.test")
                .motdepasse("MotDePasse123!")
                .numTel("690000000")
                .dateNaissance(Instant.parse("1985-01-01T00:00:00Z"))
                .adresse(AdresseDto.builder().adresse1("Rue 1").ville("Douala").pays("Cameroun").build())
                .build());
        return inscription;
    }

    @Test
    void le_super_administrateur_inscrit_une_entreprise_et_son_administrateur() {
        connecte(ERole.ROLE_SUPER_ADMIN);
        InscriptionEntrepriseDto demande = inscription();

        EntrepriseDto entreprise = entrepriseService.inscrire(demande);

        assertThat(entreprise.getId()).isNotNull();
        // Par `findAllByEntrepriseId`, qui charge les roles avec le compte : les lire depuis un
        // `findById` hors transaction partirait en LazyInitializationException.
        Utilisateur admin = utilisateurRepository.findAllByEntrepriseId(entreprise.getId()).stream()
                .filter(u -> demande.getAdministrateur().getUsername().equals(u.getUsername()))
                .findFirst().orElseThrow();
        // Le compte nait rattache : c'est tout l'interet de faire les deux d'un coup.
        assertThat(admin.getEntreprise().getId()).isEqualTo(entreprise.getId());
        assertThat(admin.getRoles()).extracting(r -> r.getRoleName()).containsExactly(ERole.ROLE_ADMIN);
        assertThat(admin.isActif()).isTrue();
        assertThat(encodeur.matches("MotDePasse123!", admin.getMotdepasse())).isTrue();
    }

    @Test
    void un_administrateur_ordinaire_n_inscrit_pas_d_entreprise() {
        connecte(ERole.ROLE_ADMIN);

        // Il administre la sienne ; creer des entreprises est le metier de l'editeur.
        assertThatThrownBy(() -> entrepriseService.inscrire(inscription()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void un_identifiant_deja_pris_est_refuse_et_n_laisse_aucune_entreprise() {
        connecte(ERole.ROLE_SUPER_ADMIN);
        InscriptionEntrepriseDto premiere = inscription();
        entrepriseService.inscrire(premiere);

        InscriptionEntrepriseDto seconde = inscription();
        seconde.getAdministrateur().setUsername(premiere.getAdministrateur().getUsername());
        String nomEntreprise = seconde.getEntreprise().getNom();

        assertThatThrownBy(() -> entrepriseService.inscrire(seconde))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("déjà pris");

        // Tout est dans la meme transaction : l'entreprise ne reste pas derriere, orpheline.
        assertThat(entrepriseService.findAll())
                .extracting(EntrepriseDto::getNom)
                .doesNotContain(nomEntreprise);
    }

    @Test
    void une_inscription_sans_administrateur_est_refusee() {
        connecte(ERole.ROLE_SUPER_ADMIN);
        InscriptionEntrepriseDto sansAdmin = inscription();
        sansAdmin.setAdministrateur(null);

        assertThatThrownBy(() -> entrepriseService.inscrire(sansAdmin))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("administrateur");
    }

    @Test
    void l_entreprise_inscrite_collecte_la_tva_au_taux_camerounais() {
        connecte(ERole.ROLE_SUPER_ADMIN);

        EntrepriseDto entreprise = entrepriseService.inscrire(inscription());

        assertThat(entreprise.getAssujettieTva()).isTrue();
        assertThat(entreprise.getTauxTva()).isEqualByComparingTo("19.25");
    }
}
