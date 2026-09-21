package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FactureDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneFactureDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
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
 * La facturation d'une vente.
 *
 * Rien ne calculait de montant dans cette application : une vente portait des lignes et personne
 * n'en faisait la somme. Ces tests portent surtout sur l'arithmetique — c'est la qu'une erreur se
 * paie, et qu'elle se voit le plus tard.
 */
class FacturationTest extends AbstractIntegrationTest {

    @Autowired
    private FactureService factureService;
    @Autowired
    private VenteService venteService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private MvtStkService mvtStkService;

    private CategoryDto category;
    private Long idArticle;

    @BeforeEach
    void preparer() {
        category = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID())
                .designation("Boissons")
                .build());
        idArticle = creerArticle("2000", "19.25");
        approvisionner(idArticle);
    }

    private Long creerArticle(String prix, String tva) {
        return articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID())
                .designation("Article facture")
                .prixUnitaireHt(new BigDecimal(prix))
                .tauxTva(new BigDecimal(tva))
                .category(category)
                .build()).getId();
    }

    private void approvisionner(Long id) {
        mvtStkService.entreeStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(id).build())
                .quantite(new BigDecimal("500"))
                .build());
    }

    private VenteDto vendre(Long id, String quantite, String prix) {
        return venteService.save(VenteDto.builder()
                .code("VTE-" + UUID.randomUUID())
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(id).build())
                        .quantite(new BigDecimal(quantite))
                        .prixUnitaire(new BigDecimal(prix))
                        .build()))
                .build());
    }

    @Test
    void la_facture_calcule_ht_tva_et_ttc() {
        VenteDto vente = vendre(idArticle, "3", "2000");

        FactureDto facture = factureService.emettre(vente.getId());

        // 3 x 2000 = 6000 HT ; 19,25 % = 1155 ; 7155 TTC.
        assertThat(facture.getTotalHt()).isEqualByComparingTo("6000.00");
        assertThat(facture.getTotalTva()).isEqualByComparingTo("1155.00");
        assertThat(facture.getTotalTtc()).isEqualByComparingTo("7155.00");
        assertThat(facture.getNumero()).startsWith("FA-");
    }

    @Test
    void le_total_est_la_somme_des_lignes_affichees() {
        Long autre = creerArticle("1500", "19.25");
        approvisionner(autre);

        VenteDto vente = venteService.save(VenteDto.builder()
                .code("VTE-" + UUID.randomUUID())
                .ligneVente(List.of(
                        LigneVenteDto.builder().article(ArticleDto.builder().Id(idArticle).build())
                                .quantite(new BigDecimal("2")).prixUnitaire(new BigDecimal("2000")).build(),
                        LigneVenteDto.builder().article(ArticleDto.builder().Id(autre).build())
                                .quantite(new BigDecimal("3")).prixUnitaire(new BigDecimal("1500")).build()))
                .build());

        FactureDto facture = factureService.emettre(vente.getId());

        // Le total doit etre l'addition de ce que le client a sous les yeux, au centime pres.
        BigDecimal sommeDesLignes = facture.getLignes().stream()
                .map(LigneFactureDto::getMontantTtc)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(facture.getTotalTtc()).isEqualByComparingTo(sommeDesLignes);
        assertThat(facture.getTotalHt()).isEqualByComparingTo("8500.00");
    }

    @Test
    void la_ligne_fige_le_prix_de_la_vente_et_non_celui_de_l_article() {
        VenteDto vente = vendre(idArticle, "2", "1800");

        FactureDto facture = factureService.emettre(vente.getId());

        // L'article vaut 2000, mais la vente s'est faite a 1800 : c'est ce prix qui fait foi.
        assertThat(facture.getLignes().get(0).getPrixUnitaireHt()).isEqualByComparingTo("1800");
        assertThat(facture.getTotalHt()).isEqualByComparingTo("3600.00");
    }

    @Test
    void une_facture_ne_bouge_plus_quand_le_prix_de_l_article_change() {
        VenteDto vente = vendre(idArticle, "2", "2000");
        FactureDto facture = factureService.emettre(vente.getId());

        ArticleDto article = articleService.findById(idArticle);
        article.setPrixUnitaireHt(new BigDecimal("9999"));
        articleService.save(article);

        // C'est la raison d'etre d'un document fige : celle que le client detient ne change pas.
        FactureDto relue = factureService.findById(facture.getId());
        assertThat(relue.getTotalHt()).isEqualByComparingTo("4000.00");
        assertThat(relue.getLignes().get(0).getPrixUnitaireHt()).isEqualByComparingTo("2000");
    }

    @Test
    void une_vente_ne_se_facture_qu_une_fois() {
        VenteDto vente = vendre(idArticle, "1", "2000");
        factureService.emettre(vente.getId());

        assertThatThrownBy(() -> factureService.emettre(vente.getId()))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("déjà facturée");
    }

    @Test
    void deux_factures_portent_deux_numeros_differents() {
        FactureDto premiere = factureService.emettre(vendre(idArticle, "1", "2000").getId());
        FactureDto seconde = factureService.emettre(vendre(idArticle, "1", "2000").getId());

        assertThat(premiere.getNumero()).isNotEqualTo(seconde.getNumero());
    }

    @Test
    void une_vente_annulee_ne_se_facture_pas() {
        VenteDto vente = vendre(idArticle, "1", "2000");
        venteService.annuler(vente.getId());

        assertThatThrownBy(() -> factureService.emettre(vente.getId()))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("ne se facture pas");
    }

    @Test
    void une_vente_facturee_ne_se_corrige_plus() {
        VenteDto vente = vendre(idArticle, "4", "2000");
        factureService.emettre(vente.getId());
        Long idLigne = venteService.lignes(vente.getId()).get(0).getId();

        assertThatThrownBy(() -> venteService.modifierQuantite(vente.getId(), idLigne, new BigDecimal("1")))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("facturée");

        assertThatThrownBy(() -> venteService.annuler(vente.getId()))
                .isInstanceOf(InvalidEntityException.class);
    }

    @Test
    void annuler_la_facture_rouvre_la_vente() {
        VenteDto vente = vendre(idArticle, "4", "2000");
        FactureDto facture = factureService.emettre(vente.getId());

        FactureDto annulee = factureService.annuler(facture.getId());

        assertThat(annulee.isAnnulee()).isTrue();
        // La facture reste lisible : un numero emis puis disparu, c'est ce qu'une comptabilite ne
        // doit pas montrer.
        assertThat(factureService.findById(facture.getId())).isNotNull();

        Long idLigne = venteService.lignes(vente.getId()).get(0).getId();
        venteService.modifierQuantite(vente.getId(), idLigne, new BigDecimal("2"));
        assertThat(venteService.lignes(vente.getId()).get(0).getQuantite()).isEqualByComparingTo("2");
    }

    @Test
    void la_facture_se_retrouve_par_son_numero_et_par_sa_vente() {
        VenteDto vente = vendre(idArticle, "1", "2000");
        FactureDto emise = factureService.emettre(vente.getId());

        assertThat(factureService.findByNumero(emise.getNumero()).getId()).isEqualTo(emise.getId());
        assertThat(factureService.findByVente(vente.getId()).getNumero()).isEqualTo(emise.getNumero());
    }
}
