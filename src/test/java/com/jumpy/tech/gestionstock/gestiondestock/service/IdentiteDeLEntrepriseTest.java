package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.AdresseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.validator.EntrepriseValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * L'identite de l'entreprise, celle que le ticket de caisse imprime en en-tete.
 *
 * Elle se fixait a l'inscription et ne se corrigeait plus : ni l'adresse, ni le telephone, ni le
 * registre de commerce, ni le regime de TVA. Or c'est precisement ce qui figure sur le papier
 * remis au client, et un magasin change d'adresse. Le NIU et le logo n'existaient pas du tout.
 */
class IdentiteDeLEntrepriseTest extends AbstractIntegrationTest {

    /** Un PNG d'un pixel : la plus petite image qui soit vraiment une image. */
    private static final String LOGO =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8"
                    + "z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg==";

    @Autowired
    private EntrepriseService entrepriseService;

    private Long idEntreprise;

    @BeforeEach
    void uneEntrepriseEtSonGerant() {
        idEntreprise = creer();
        connecteChez(idEntreprise);
    }

    @AfterEach
    void oublierLUtilisateur() {
        // Le contexte de securite est porte par le fil d'execution, qui est reutilise d'un test a
        // l'autre : sans ce nettoyage, le suivant heriterait de cet utilisateur.
        SecurityContextHolder.clearContext();
    }

    private Long creer() {
        return entrepriseService.save(EntrepriseDto.builder()
                .nom("Quincaillerie " + UUID.randomUUID())
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .build()).getId();
    }

    private void connecteChez(Long id) {
        UserDetailsImpl principal = new UserDetailsImpl(1L, "gerant", "gerant@exemple.test",
                "x", id, List.of(new SimpleGrantedAuthority(ERole.ROLE_ADMIN.name())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    /** L'identite telle qu'elle est, prete a etre modifiee sur un champ. */
    private EntrepriseDto telleQuelle() {
        return entrepriseService.mienne();
    }

    @Test
    void le_gerant_lit_l_entreprise_pour_laquelle_il_travaille() {
        assertThat(telleQuelle().getId()).isEqualTo(idEntreprise);
    }

    @Test
    void l_adresse_et_le_telephone_se_corrigent() {
        EntrepriseDto dto = telleQuelle();
        dto.setTel("699112233");
        dto.setAdresse(AdresseDto.builder()
                .adresse1("Rue de la Réunification")
                .ville("Douala")
                .pays("Cameroun")
                .build());

        entrepriseService.mettreAJourMienne(dto);

        EntrepriseDto relue = telleQuelle();
        assertThat(relue.getTel()).isEqualTo("699112233");
        assertThat(relue.getAdresse().getVille()).isEqualTo("Douala");
        assertThat(relue.getAdresse().getAdresse1()).isEqualTo("Rue de la Réunification");
    }

    @Test
    void le_niu_et_le_logo_s_enregistrent() {
        EntrepriseDto dto = telleQuelle();
        dto.setNiu("M021912345678L");
        dto.setLogo(LOGO);

        entrepriseService.mettreAJourMienne(dto);

        EntrepriseDto relue = telleQuelle();
        assertThat(relue.getNiu()).isEqualTo("M021912345678L");
        assertThat(relue.getLogo()).isEqualTo(LOGO);
    }

    @Test
    void le_regime_de_tva_se_change() {
        EntrepriseDto dto = telleQuelle();
        dto.setAssujettieTva(false);

        entrepriseService.mettreAJourMienne(dto);

        assertThat(telleQuelle().getAssujettieTva()).isFalse();
    }

    @Test
    void l_identifiant_porte_par_le_corps_de_la_requete_est_ignore() {
        Long idVoisine = creer();
        String nomDeLaVoisine = entrepriseService.findById(idVoisine).getNom();

        // Le gerant reste connecte chez lui, mais designe l'entreprise d'a cote. Sans la lecture
        // de l'entreprise courante dans le jeton, c'est celle du voisin qui serait renommee.
        EntrepriseDto dto = telleQuelle();
        dto.setId(idVoisine);
        dto.setNom("Renommée par le voisin");

        entrepriseService.mettreAJourMienne(dto);

        assertThat(entrepriseService.findById(idVoisine).getNom()).isEqualTo(nomDeLaVoisine);
        assertThat(telleQuelle().getNom()).isEqualTo("Renommée par le voisin");
    }

    @Test
    void une_entreprise_sans_nom_est_refusee() {
        EntrepriseDto dto = telleQuelle();
        dto.setNom(null);

        assertThatThrownBy(() -> entrepriseService.mettreAJourMienne(dto))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("pas valide");
    }

    @Test
    void un_logo_qui_n_est_pas_une_image_est_refuse() {
        EntrepriseDto dto = telleQuelle();
        // Une adresse web, justement ce qu'on ne veut pas stocker : elle cesserait un jour de
        // repondre et les tickets sortiraient avec un carre vide.
        dto.setLogo("https://exemple.test/logo.png");

        assertThatThrownBy(() -> entrepriseService.mettreAJourMienne(dto))
                .isInstanceOf(InvalidEntityException.class);
        assertThat(telleQuelle().getLogo()).isNull();
    }

    @Test
    void un_logo_trop_lourd_est_refuse() {
        EntrepriseDto dto = telleQuelle();
        dto.setLogo("data:image/png;base64," + "A".repeat(EntrepriseValidator.LOGO_MAX));

        assertThatThrownBy(() -> entrepriseService.mettreAJourMienne(dto))
                .isInstanceOf(InvalidEntityException.class);
    }

    @Test
    void un_taux_de_tva_hors_bornes_est_refuse() {
        EntrepriseDto dto = telleQuelle();
        dto.setTauxTva(new BigDecimal("120"));

        assertThatThrownBy(() -> entrepriseService.mettreAJourMienne(dto))
                .isInstanceOf(InvalidEntityException.class);
    }

    @Test
    void un_compte_rattache_a_aucune_entreprise_ne_met_rien_a_jour() {
        EntrepriseDto dto = telleQuelle();
        connecteChez(null);

        assertThatThrownBy(() -> entrepriseService.mettreAJourMienne(dto))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
