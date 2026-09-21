package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FactureDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ReglementDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ModeReglement;
import com.jumpy.tech.gestionstock.gestiondestock.entities.StatutReglement;
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
 * Le reglement des factures.
 *
 * Une facture etait emise ou annulee, jamais reglee : rien ne disait ce qui avait ete encaisse ni
 * ce qui restait du. Les paiements partiels sont la regle et non l'exception — un acompte a la
 * commande, le solde a la livraison.
 */
class ReglementDesFacturesTest extends AbstractIntegrationTest {

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

    private FactureDto facture;

    @BeforeEach
    void uneFactureDe11925() {
        CategoryDto category = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID()).designation("Divers").build());
        Long idArticle = articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID())
                .designation("Article")
                .prixUnitaireHt(new BigDecimal("10000"))
                .tauxTva(new BigDecimal("19.25"))
                .category(category)
                .build()).getId();
        mvtStkService.entreeStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(idArticle).build())
                .quantite(new BigDecimal("100")).build());

        VenteDto vente = venteService.save(VenteDto.builder()
                .code("VTE-" + UUID.randomUUID())
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(BigDecimal.ONE)
                        .prixUnitaire(new BigDecimal("10000"))
                        .build()))
                .build());
        facture = factureService.emettre(vente.getId());
    }

    private ReglementDto reglementDe(String montant, ModeReglement mode) {
        return ReglementDto.builder()
                .montant(new BigDecimal(montant))
                .mode(mode)
                .reference("REF-" + UUID.randomUUID())
                .build();
    }

    @Test
    void une_facture_emise_est_impayee() {
        assertThat(facture.getTotalTtc()).isEqualByComparingTo("11925.00");
        assertThat(facture.getMontantRegle()).isEqualByComparingTo("0");
        assertThat(facture.getResteAPayer()).isEqualByComparingTo("11925.00");
        assertThat(facture.getStatutReglement()).isEqualTo(StatutReglement.IMPAYEE);
    }

    @Test
    void un_acompte_laisse_la_facture_partiellement_reglee() {
        factureService.regler(facture.getId(), reglementDe("5000", ModeReglement.MOBILE_MONEY));

        FactureDto relue = factureService.findById(facture.getId());
        assertThat(relue.getMontantRegle()).isEqualByComparingTo("5000.00");
        assertThat(relue.getResteAPayer()).isEqualByComparingTo("6925.00");
        assertThat(relue.getStatutReglement()).isEqualTo(StatutReglement.PARTIELLEMENT_REGLEE);
    }

    @Test
    void le_solde_rend_la_facture_reglee() {
        factureService.regler(facture.getId(), reglementDe("5000", ModeReglement.ESPECES));
        factureService.regler(facture.getId(), reglementDe("6925", ModeReglement.MOBILE_MONEY));

        FactureDto relue = factureService.findById(facture.getId());
        assertThat(relue.getResteAPayer()).isEqualByComparingTo("0");
        assertThat(relue.getStatutReglement()).isEqualTo(StatutReglement.REGLEE);
        assertThat(factureService.reglements(facture.getId())).hasSize(2);
    }

    @Test
    void un_reglement_qui_depasse_le_reste_a_payer_est_refuse() {
        factureService.regler(facture.getId(), reglementDe("10000", ModeReglement.ESPECES));

        // Un trop-percu est une erreur de saisie, pas une situation a enregistrer.
        assertThatThrownBy(() -> factureService.regler(facture.getId(), reglementDe("5000", ModeReglement.ESPECES)))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("dépasse le reste à payer");

        assertThat(factureService.findById(facture.getId()).getMontantRegle())
                .isEqualByComparingTo("10000.00");
    }

    @Test
    void on_ne_regle_pas_deux_fois_une_facture_soldee() {
        factureService.regler(facture.getId(), reglementDe("11925", ModeReglement.VIREMENT));

        assertThatThrownBy(() -> factureService.regler(facture.getId(), reglementDe("100", ModeReglement.ESPECES)))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("déjà réglée");
    }

    @Test
    void un_montant_nul_ou_negatif_est_refuse() {
        assertThatThrownBy(() -> factureService.regler(facture.getId(), reglementDe("0", ModeReglement.ESPECES)))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("strictement positif");

        assertThatThrownBy(() -> factureService.regler(facture.getId(), reglementDe("-500", ModeReglement.ESPECES)))
                .isInstanceOf(InvalidEntityException.class);
    }

    @Test
    void un_reglement_dit_par_quel_moyen_il_a_ete_recu() {
        assertThatThrownBy(() -> factureService.regler(facture.getId(),
                ReglementDto.builder().montant(new BigDecimal("1000")).build()))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("par quel moyen");
    }

    @Test
    void une_facture_annulee_ne_se_regle_pas() {
        factureService.annuler(facture.getId());

        assertThatThrownBy(() -> factureService.regler(facture.getId(), reglementDe("1000", ModeReglement.ESPECES)))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("annulée");
    }

    @Test
    void une_facture_deja_encaissee_ne_s_annule_pas_sans_reprendre_les_reglements() {
        factureService.regler(facture.getId(), reglementDe("5000", ModeReglement.ESPECES));

        // Sinon de l'argent recu resterait sans rien en face.
        assertThatThrownBy(() -> factureService.annuler(facture.getId()))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("règlements");

        Long idReglement = factureService.reglements(facture.getId()).get(0).getId();
        factureService.supprimerReglement(facture.getId(), idReglement);

        // Une fois l'encaissement repris, l'annulation redevient possible.
        assertThat(factureService.annuler(facture.getId()).isAnnulee()).isTrue();
    }

    @Test
    void reprendre_un_reglement_remet_le_reste_a_payer() {
        factureService.regler(facture.getId(), reglementDe("11925", ModeReglement.CHEQUE));
        assertThat(factureService.findById(facture.getId()).getStatutReglement())
                .isEqualTo(StatutReglement.REGLEE);

        // Le cheque est revenu impaye : l'encaissement se reprend.
        Long idReglement = factureService.reglements(facture.getId()).get(0).getId();
        factureService.supprimerReglement(facture.getId(), idReglement);

        FactureDto relue = factureService.findById(facture.getId());
        assertThat(relue.getResteAPayer()).isEqualByComparingTo("11925.00");
        assertThat(relue.getStatutReglement()).isEqualTo(StatutReglement.IMPAYEE);
    }

    @Test
    void un_reglement_d_une_autre_facture_ne_se_supprime_pas_par_ce_chemin() {
        factureService.regler(facture.getId(), reglementDe("1000", ModeReglement.ESPECES));
        Long idReglement = factureService.reglements(facture.getId()).get(0).getId();

        // Connaitre un identifiant de reglement ne doit pas suffire a effacer une recette portee
        // par la facture d'un autre.
        assertThatThrownBy(() -> factureService.supprimerReglement(facture.getId() + 1_000_000, idReglement))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void la_liste_des_factures_porte_le_reste_a_payer() {
        factureService.regler(facture.getId(), reglementDe("2000", ModeReglement.MOBILE_MONEY));

        FactureDto danslaListe = factureService.findAll(org.springframework.data.domain.PageRequest.of(0, 50))
                .getContent().stream()
                .filter(f -> f.getId().equals(facture.getId()))
                .findFirst().orElseThrow();

        assertThat(danslaListe.getResteAPayer()).isEqualByComparingTo("9925.00");
        assertThat(danslaListe.getStatutReglement()).isEqualTo(StatutReglement.PARTIELLEMENT_REGLEE);
    }
}
