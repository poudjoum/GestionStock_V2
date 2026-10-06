package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommerceDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.SanteCommerce;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Utilisateur;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EntrepriseRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.UtilisateurRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** R5 : l'editeur voit qui vend, qui ralentit, qui decroche, et qui n'a jamais commence. */
class SuiviDesCommercesTest extends AbstractIntegrationTest {

    @Autowired
    private PlateformeService plateformeService;
    @Autowired
    private EntrepriseService entrepriseService;
    @Autowired
    private EntrepriseRepository entrepriseRepository;
    @Autowired
    private UtilisateurRepository utilisateurRepository;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private MvtStkService mvtStkService;
    @Autowired
    private VenteService venteService;

    @AfterEach
    void oublier() {
        SecurityContextHolder.clearContext();
    }

    private void connecte(Long idEntreprise, ERole role) {
        UserDetailsImpl principal = new UserDetailsImpl(1L, "essai", "essai@exemple.test", "x", idEntreprise,
                List.of(new SimpleGrantedAuthority(role.name())));
        principal.setIdsSites(Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    /** Un commerce qui a vendu il y a `joursAvant` jours ; nul : il n'a jamais vendu. */
    private Long commerce(Integer joursAvant) {
        SecurityContextHolder.clearContext();
        Long id = entrepriseService.save(EntrepriseDto.builder()
                .nom("Commerce " + UUID.randomUUID())
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .build()).getId();
        if (joursAvant == null) {
            return id;
        }
        connecte(id, ERole.ROLE_ADMIN);
        CategoryDto rayon = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID()).designation("Divers").build());
        Long article = articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID()).designation("Savon")
                .prixUnitaireHt(new BigDecimal("500")).category(rayon).build()).getId();
        mvtStkService.entreeStock(MvtStkDto.builder().article(ArticleDto.builder().Id(article).build())
                .quantite(new BigDecimal("10")).build());
        venteService.synchroniser(VenteDto.builder()
                .code("V-" + UUID.randomUUID())
                .referenceClient(UUID.randomUUID().toString())
                .datevente(Instant.now().minus(joursAvant, ChronoUnit.DAYS).minus(1, ChronoUnit.HOURS))
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(article).build())
                        .quantite(new BigDecimal("2")).build()))
                .build());
        return id;
    }

    private CommerceDto ligne(List<CommerceDto> lignes, Long id) {
        return lignes.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void chaque_commerce_a_son_etat_et_ceux_qui_decrochent_passent_devant() {
        Long actif = commerce(0);
        Long ralentit = commerce(6);
        Long decroche = commerce(20);
        Long jamais = commerce(null);

        // Un compte d'un commerce actif s'est servi de l'outil aujourd'hui.
        Utilisateur gerant = new Utilisateur("g-" + UUID.randomUUID(), "g" + UUID.randomUUID() + "@exemple.test", "x");
        gerant.setEntreprise(entrepriseRepository.findById(actif).orElseThrow());
        Long idGerant = utilisateurRepository.save(gerant).getId();
        utilisateurRepository.noterActivite(idGerant, Instant.now());

        connecte(null, ERole.ROLE_SUPER_ADMIN);
        List<CommerceDto> lignes = plateformeService.commerces();

        assertThat(ligne(lignes, actif).sante()).isEqualTo(SanteCommerce.ACTIF);
        assertThat(ligne(lignes, ralentit).sante()).isEqualTo(SanteCommerce.RALENTIT);
        assertThat(ligne(lignes, decroche).sante()).isEqualTo(SanteCommerce.DECROCHE);
        assertThat(ligne(lignes, jamais).sante()).isEqualTo(SanteCommerce.PAS_DEMARRE);

        CommerceDto a = ligne(lignes, actif);
        assertThat(a.ventes30j()).isEqualTo(1);
        assertThat(a.chiffre30j()).isEqualByComparingTo("1000");
        assertThat(a.semaines()).hasSize(8).endsWith(1L);
        assertThat(a.comptesActifs()).isEqualTo(1);
        // Vingt jours : dans le mois, dans la troisieme semaine glissante.
        assertThat(ligne(lignes, decroche).semaines().get(5)).isEqualTo(1L);

        List<Long> ordre = lignes.stream().map(CommerceDto::id)
                .filter(id -> List.of(actif, ralentit, decroche, jamais).contains(id)).toList();
        assertThat(ordre).containsExactly(decroche, ralentit, jamais, actif);

        assertThat(plateformeService.resume().decrochent()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void un_mois_bien_en_dessous_du_precedent_ralentit() {
        assertThat(SanteCommerce.de(50, 1L, new BigDecimal("40000"), new BigDecimal("100000")))
                .isEqualTo(SanteCommerce.RALENTIT);
        assertThat(SanteCommerce.de(50, 1L, new BigDecimal("90000"), new BigDecimal("100000")))
                .isEqualTo(SanteCommerce.ACTIF);
    }
}
