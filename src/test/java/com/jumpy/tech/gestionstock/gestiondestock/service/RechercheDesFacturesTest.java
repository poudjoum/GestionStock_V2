package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FactureDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ReglementDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ModeReglement;
import com.jumpy.tech.gestionstock.gestiondestock.entities.StatutReglement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La recherche dans les factures.
 *
 * « Qui me doit de l'argent » est la question du comptable, et la liste paginee n'y repondait
 * pas : il fallait feuilleter toutes les factures en lisant les statuts un par un.
 *
 * Le statut de reglement n'est pas une colonne — il se deduit de la somme des reglements, comme
 * le stock se deduit de ses mouvements. Le filtre porte donc sur cette somme.
 */
class RechercheDesFacturesTest extends AbstractIntegrationTest {

    @Autowired
    private FactureService factureService;
    @Autowired
    private VenteService venteService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private MvtStkService mvtStkService;

    private Long idArticle;

    @BeforeEach
    void unArticleApprovisionne() {
        CategoryDto category = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID()).designation("Divers").build());
        idArticle = articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID())
                .designation("Sac de ciment")
                .prixUnitaireHt(new BigDecimal("1000"))
                .category(category)
                .build()).getId();
        mvtStkService.entreeStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(idArticle).build())
                .quantite(new BigDecimal("500")).build());
    }

    /** Emet une facture de `quantite` x 1000 F HT, au nom du client donne. */
    private FactureDto facture(String nomClient, String quantite) {
        ClientDto client = nomClient == null ? null : clientService.save(ClientDto.builder()
                .nom(nomClient).prenoms("X")
                .mail(UUID.randomUUID() + "@exemple.test").numTel("690000000").build());
        VenteDto vente = venteService.save(VenteDto.builder()
                .code("V-" + UUID.randomUUID())
                .client(client)
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal(quantite))
                        .prixUnitaire(new BigDecimal("1000"))
                        .build()))
                .build());
        return factureService.emettre(vente.getId());
    }

    private void regler(FactureDto facture, BigDecimal montant) {
        factureService.regler(facture.getId(), ReglementDto.builder()
                .montant(montant)
                .mode(ModeReglement.ESPECES)
                .build());
    }

    @Test
    void chercher_une_facture_par_son_numero() {
        FactureDto emise = facture("Chantier", "2");

        var trouvees = factureService.rechercher(emise.getNumero(), null, PageRequest.of(0, 20));

        assertThat(trouvees.getContent()).extracting(FactureDto::getId).containsExactly(emise.getId());
    }

    @Test
    void chercher_une_facture_par_le_nom_du_client() {
        String nom = "Quincaillerie" + UUID.randomUUID().toString().substring(0, 8);
        FactureDto emise = facture(nom, "2");

        var trouvees = factureService.rechercher(nom.toLowerCase(), null, PageRequest.of(0, 20));

        assertThat(trouvees.getContent()).extracting(FactureDto::getId).containsExactly(emise.getId());
    }

    @Test
    void les_impayees_sont_celles_sur_lesquelles_rien_n_est_entre() {
        FactureDto impayee = facture("Sans", "3");
        FactureDto reglee = facture("Avec", "2");
        regler(reglee, reglee.getTotalTtc());

        var trouvees = factureService.rechercher(impayee.getNumero(), "IMPAYEE", PageRequest.of(0, 20));

        assertThat(trouvees.getContent()).extracting(FactureDto::getId).containsExactly(impayee.getId());
        assertThat(factureService.rechercher(reglee.getNumero(), "IMPAYEE", PageRequest.of(0, 20))
                .getContent()).isEmpty();
    }

    @Test
    void une_facture_partiellement_reglee_a_son_propre_statut() {
        FactureDto facture = facture("Acompte", "4");
        regler(facture, new BigDecimal("1000"));

        var partielles = factureService.rechercher(
                facture.getNumero(), "PARTIELLEMENT_REGLEE", PageRequest.of(0, 20));

        assertThat(partielles.getContent()).hasSize(1);
        assertThat(partielles.getContent().get(0).getStatutReglement())
                .isEqualTo(StatutReglement.PARTIELLEMENT_REGLEE);
        // Elle n'est ni dans les impayees ni dans les reglees : un acompte n'est pas rien, et ce
        // n'est pas tout.
        assertThat(factureService.rechercher(facture.getNumero(), "IMPAYEE", PageRequest.of(0, 20))
                .getContent()).isEmpty();
        assertThat(factureService.rechercher(facture.getNumero(), "REGLEE", PageRequest.of(0, 20))
                .getContent()).isEmpty();
    }

    @Test
    void les_dues_reunissent_l_impaye_et_le_partiel() {
        FactureDto impayee = facture("Rien", "3");
        FactureDto partielle = facture("Acompte", "4");
        regler(partielle, new BigDecimal("500"));
        FactureDto reglee = facture("Solde", "2");
        regler(reglee, reglee.getTotalTtc());

        // C'est la question que le comptable pose vraiment : sur quoi reste-t-il a encaisser.
        var dues = factureService.rechercher(null, "DUES", PageRequest.of(0, 500));

        assertThat(dues.getContent()).extracting(FactureDto::getId)
                .contains(impayee.getId(), partielle.getId())
                .doesNotContain(reglee.getId());
    }

    @Test
    void une_facture_annulee_ne_doit_plus_rien() {
        FactureDto facture = facture("Annulee", "3");
        factureService.annuler(facture.getId());

        // Sans cette exclusion, le comptable courrait apres un argent que personne ne doit.
        assertThat(factureService.rechercher(facture.getNumero(), "DUES", PageRequest.of(0, 20))
                .getContent()).isEmpty();
        assertThat(factureService.rechercher(facture.getNumero(), "IMPAYEE", PageRequest.of(0, 20))
                .getContent()).isEmpty();
        assertThat(factureService.rechercher(facture.getNumero(), "ANNULEE", PageRequest.of(0, 20))
                .getContent()).hasSize(1);
    }

    @Test
    void sans_filtre_toutes_les_factures_reviennent() {
        facture("Une", "1");
        facture("Deux", "1");

        long avecVides = factureService.rechercher("", "", PageRequest.of(0, 500)).getTotalElements();
        long sansRien = factureService.findAll(PageRequest.of(0, 500)).getTotalElements();

        assertThat(avecVides).isEqualTo(sansRien).isGreaterThanOrEqualTo(2);
    }

    @Test
    void la_recherche_garde_le_montant_regle_de_chaque_facture() {
        FactureDto facture = facture("Acompte", "4");
        regler(facture, new BigDecimal("1200"));

        var relue = factureService.rechercher(facture.getNumero(), null, PageRequest.of(0, 20))
                .getContent().get(0);

        // Le filtre ne doit pas court-circuiter le calcul du reste a payer : c'est la colonne que
        // le comptable regarde.
        assertThat(relue.getMontantRegle()).isEqualByComparingTo("1200");
        assertThat(relue.getResteAPayer())
                .isEqualByComparingTo(facture.getTotalTtc().subtract(new BigDecimal("1200")));
    }
}
