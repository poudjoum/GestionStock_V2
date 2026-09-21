package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneVenteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le parcours qui n'existait pas : une vente retire du magasin ce qu'elle vend.
 */
class VenteEtStockIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ArticleService articleService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private VenteService venteService;
    @Autowired
    private MvtStkService mvtStkService;
    @Autowired
    private LigneVenteRepository ligneVenteRepository;

    private Long idArticle;

    @BeforeEach
    void creerUnArticleEnStock() {
        CategoryDto category = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID())
                .designation("Boissons")
                .build());

        ArticleDto article = articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID())
                .designation("Eau minerale")
                .prixUnitaireHt(new BigDecimal("500"))
                .tauxTva(new BigDecimal("19.25"))
                .category(category)
                .build());

        idArticle = article.getId();

        mvtStkService.entreeStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(idArticle).build())
                .quantite(new BigDecimal("40"))
                .build());
    }

    @Test
    void une_vente_diminue_le_stock_et_enregistre_ses_lignes() {
        venteService.save(vente("VTE-" + UUID.randomUUID(), "15"));

        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("25");
        // Les lignes n'etaient pas ecrites du tout : une vente se resumait a un en-tete vide.
        assertThat(ligneVenteRepository.findAll())
                .anyMatch(ligne -> ligne.getArticles() != null
                        && idArticle.equals(ligne.getArticles().getId()));
    }

    @Test
    void deux_ventes_se_cumulent_sur_le_meme_article() {
        venteService.save(vente("VTE-" + UUID.randomUUID(), "10"));
        venteService.save(vente("VTE-" + UUID.randomUUID(), "12"));

        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("18");
    }

    @Test
    void une_vente_au_dela_du_stock_est_refusee_et_ne_laisse_aucune_trace() {
        String code = "VTE-" + UUID.randomUUID();

        assertThatThrownBy(() -> venteService.save(vente(code, "41")))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("Stock insuffisant");

        // La transaction couvre l'en-tete, les lignes et le mouvement : un refus sur la derniere
        // etape ne doit pas laisser une vente a moitie enregistree.
        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("40");
        assertThat(venteService.findAll()).noneMatch(v -> code.equals(v.getCode()));
    }

    @Test
    void une_vente_sans_ligne_est_refusee() {
        VenteDto sansLigne = VenteDto.builder().code("VTE-" + UUID.randomUUID()).build();

        assertThatThrownBy(() -> venteService.save(sansLigne))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("ne vend rien");
    }

    private VenteDto vente(String code, String quantite) {
        return VenteDto.builder()
                .code(code)
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal(quantite))
                        .prixUnitaire(new BigDecimal("500"))
                        .build()))
                .build();
    }
}
