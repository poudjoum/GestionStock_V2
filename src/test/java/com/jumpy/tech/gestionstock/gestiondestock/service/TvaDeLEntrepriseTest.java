package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FactureDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le regime de TVA se parametre sur l'entreprise.
 *
 * Il n'existait que sur l'article, ou il fallait le redire a chaque creation, et rien ne permettait
 * de dire qu'une entreprise n'est pas assujettie — toutes collectaient la TVA, ce qui est faux.
 */
class TvaDeLEntrepriseTest extends AbstractIntegrationTest {

    @Autowired
    private FactureService factureService;
    @Autowired
    private VenteService venteService;
    @Autowired
    private EntrepriseService entrepriseService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private MvtStkService mvtStkService;

    private CategoryDto category;

    @BeforeEach
    void preparer() {
        category = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID())
                .designation("Divers")
                .build());
    }

    private EntrepriseDto entreprise(Boolean assujettie, String taux) {
        return entrepriseService.save(EntrepriseDto.builder()
                .nom("Entreprise " + UUID.randomUUID())
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .assujettieTva(assujettie)
                .tauxTva(taux == null ? null : new BigDecimal(taux))
                .build());
    }

    /** Article sans taux propre : il s'en remet a celui de l'entreprise. */
    private Long article(String tauxArticle) {
        Long id = articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID())
                .designation("Article")
                .prixUnitaireHt(new BigDecimal("1000"))
                .tauxTva(tauxArticle == null ? null : new BigDecimal(tauxArticle))
                .category(category)
                .build()).getId();
        mvtStkService.entreeStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(id).build())
                .quantite(new BigDecimal("100"))
                .build());
        return id;
    }

    private FactureDto facturer(Long idArticle, EntrepriseDto entreprise) {
        VenteDto vente = venteService.save(VenteDto.builder()
                .code("VTE-" + UUID.randomUUID())
                .idEntreprise(entreprise == null ? null : entreprise.getId())
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal("10"))
                        .prixUnitaire(new BigDecimal("1000"))
                        .build()))
                .build());
        return factureService.emettre(vente.getId());
    }

    @Test
    void une_entreprise_non_assujettie_ne_facture_aucune_tva() {
        EntrepriseDto exoneree = entreprise(false, "19.25");

        FactureDto facture = facturer(article(null), exoneree);

        assertThat(facture.getTotalHt()).isEqualByComparingTo("10000.00");
        assertThat(facture.getTotalTva()).isEqualByComparingTo("0.00");
        assertThat(facture.getTotalTtc()).isEqualByComparingTo("10000.00");
        // La mention explique le zero : sans elle, on ne distingue pas une exoneration d'un
        // calcul qui n'a pas eu lieu.
        assertThat(facture.isTvaApplicable()).isFalse();
    }

    @Test
    void le_taux_de_l_entreprise_s_applique_aux_articles_qui_n_en_fixent_pas() {
        EntrepriseDto assujettie = entreprise(true, "19.25");

        FactureDto facture = facturer(article(null), assujettie);

        assertThat(facture.getTotalTva()).isEqualByComparingTo("1925.00");
        assertThat(facture.getLignes().get(0).getTauxTva()).isEqualByComparingTo("19.25");
        assertThat(facture.isTvaApplicable()).isTrue();
    }

    @Test
    void un_taux_porte_par_l_article_l_emporte_sur_celui_de_l_entreprise() {
        EntrepriseDto assujettie = entreprise(true, "19.25");

        // Produit a taux reduit : l'exception se porte sur l'article.
        FactureDto facture = facturer(article("5.5"), assujettie);

        assertThat(facture.getLignes().get(0).getTauxTva()).isEqualByComparingTo("5.5");
        assertThat(facture.getTotalTva()).isEqualByComparingTo("550.00");
    }

    @Test
    void un_article_exonere_chez_une_entreprise_assujettie_ne_porte_pas_de_tva() {
        EntrepriseDto assujettie = entreprise(true, "19.25");

        FactureDto facture = facturer(article("0"), assujettie);

        assertThat(facture.getTotalTva()).isEqualByComparingTo("0.00");
        // L'entreprise, elle, collecte bien : c'est l'article qui est exonere.
        assertThat(facture.isTvaApplicable()).isTrue();
    }

    @Test
    void une_entreprise_non_assujettie_l_emporte_sur_le_taux_de_l_article() {
        EntrepriseDto exoneree = entreprise(false, "19.25");

        FactureDto facture = facturer(article("19.25"), exoneree);

        assertThat(facture.getTotalTva()).isEqualByComparingTo("0.00");
    }

    @Test
    void une_entreprise_enregistree_sans_precision_collecte_au_taux_camerounais() {
        EntrepriseDto sansPrecision = entreprise(null, null);

        assertThat(sansPrecision.getAssujettieTva()).isTrue();
        assertThat(sansPrecision.getTauxTva()).isEqualByComparingTo("19.25");
    }

    @Test
    void un_article_se_cree_sans_taux_de_tva() {
        // Le taux n'est plus a redire sur chaque article : c'est tout l'interet de le parametrer
        // sur l'entreprise.
        ArticleDto article = articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID())
                .designation("Article sans taux")
                .prixUnitaireHt(new BigDecimal("800"))
                .category(category)
                .build());

        assertThat(article.getId()).isNotNull();
        assertThat(article.getTauxTva()).isNull();
    }
}
