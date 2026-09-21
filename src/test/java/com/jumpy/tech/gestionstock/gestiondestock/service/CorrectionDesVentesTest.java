package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.MotifMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La correction d'une vente, et le rattrapage de stock qu'elle impose.
 *
 * Une vente a deja sorti sa marchandise quand on la corrige : on ne peut pas reecrire une ligne
 * et s'en tenir la, comme pour une commande. Chaque operation ecrit donc un mouvement de
 * compensation, et le motif permet de les distinguer d'une livraison dans l'historique.
 */
class CorrectionDesVentesTest extends AbstractIntegrationTest {

    @Autowired
    private VenteService venteService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private MvtStkService mvtStkService;

    private Long idArticle;
    private VenteDto vente;

    @BeforeEach
    void preparer() {
        CategoryDto category = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID())
                .designation("Boissons")
                .build());

        idArticle = articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID())
                .designation("Jus de fruit")
                .prixUnitaireHt(new BigDecimal("700"))
                .tauxTva(new BigDecimal("19.25"))
                .category(category)
                .build()).getId();

        mvtStkService.entreeStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(idArticle).build())
                .quantite(new BigDecimal("100"))
                .build());

        vente = venteService.save(VenteDto.builder()
                .code("VTE-" + UUID.randomUUID())
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal("20"))
                        .prixUnitaire(new BigDecimal("700"))
                        .build()))
                .build());
    }

    private Long idPremiereLigne() {
        return venteService.lignes(vente.getId()).get(0).getId();
    }

    @Test
    void une_vente_annulee_rend_sa_marchandise_sans_disparaitre() {
        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("80");

        VenteDto annulee = venteService.annuler(vente.getId());

        assertThat(annulee.isAnnulee()).isTrue();
        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("100");
        // La vente reste lisible : une recette encaissee puis rendue doit pouvoir se retrouver.
        assertThat(venteService.findById(vente.getId())).isNotNull();
    }

    @Test
    void le_retour_porte_un_motif_qui_le_distingue_d_une_livraison() {
        venteService.annuler(vente.getId());

        assertThat(mvtStkService.mvtStkArticle(idArticle))
                .extracting(MvtStkDto::getMotif)
                .contains(MotifMvtStk.ANNULATION_VENTE, MotifMvtStk.VENTE);
    }

    @Test
    void augmenter_une_quantite_sort_le_complement() {
        venteService.modifierQuantite(vente.getId(), idPremiereLigne(), new BigDecimal("30"));

        // 100 - 30, et non 100 - 20 - 30 : seule la difference est sortie.
        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("70");
    }

    @Test
    void diminuer_une_quantite_remet_la_difference() {
        venteService.modifierQuantite(vente.getId(), idPremiereLigne(), new BigDecimal("5"));

        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("95");
    }

    @Test
    void une_augmentation_que_le_magasin_ne_couvre_pas_est_refusee() {
        assertThatThrownBy(() -> venteService.modifierQuantite(vente.getId(), idPremiereLigne(), new BigDecimal("500")))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("Stock insuffisant");

        // Le refus laisse la vente et le stock dans leur etat d'origine.
        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("80");
        assertThat(venteService.lignes(vente.getId()).get(0).getQuantite()).isEqualByComparingTo("20");
    }

    @Test
    void retirer_une_ligne_remet_sa_quantite_en_magasin() {
        venteService.retirerLigne(vente.getId(), idPremiereLigne());

        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("100");
        assertThat(venteService.lignes(vente.getId())).isEmpty();
    }

    @Test
    void une_vente_annulee_ne_se_corrige_plus() {
        Long idLigne = idPremiereLigne();
        venteService.annuler(vente.getId());

        // Elle a deja rendu sa marchandise : la corriger la rendrait une seconde fois.
        assertThatThrownBy(() -> venteService.modifierQuantite(vente.getId(), idLigne, new BigDecimal("3")))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("ne se modifie plus");

        assertThatThrownBy(() -> venteService.annuler(vente.getId()))
                .isInstanceOf(InvalidEntityException.class);

        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("100");
    }

    @Test
    void une_ligne_d_une_autre_vente_ne_se_corrige_pas_par_ce_chemin() {
        Long idLigne = idPremiereLigne();
        VenteDto autre = venteService.save(VenteDto.builder()
                .code("VTE-" + UUID.randomUUID())
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal("1"))
                        .prixUnitaire(new BigDecimal("700"))
                        .build()))
                .build());

        assertThatThrownBy(() -> venteService.modifierQuantite(autre.getId(), idLigne, new BigDecimal("2")))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("n'appartient pas");
    }
}
