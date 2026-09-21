package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FactureDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCommandeClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;
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
 * Les deux facons de vendre.
 *
 * Au comptoir, la vente se construit article par article : c'est le supermarche, ou l'on ne
 * connait le panier qu'une fois le dernier article passe. Sur commande, la vente reprend ce qui a
 * ete convenu d'avance — et c'est le seul chemin par lequel elle connait son client.
 */
class VenteAuComptoirEtSurCommandeTest extends AbstractIntegrationTest {

    @Autowired
    private VenteService venteService;
    @Autowired
    private CommandeClientService commandeClientService;
    @Autowired
    private FactureService factureService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private MvtStkService mvtStkService;

    private Long idArticle;
    private Long idAutreArticle;

    @BeforeEach
    void preparer() {
        CategoryDto category = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID())
                .designation("Epicerie")
                .build());
        idArticle = creerArticle(category, "500");
        idAutreArticle = creerArticle(category, "1200");
        approvisionner(idArticle);
        approvisionner(idAutreArticle);
    }

    private Long creerArticle(CategoryDto category, String prix) {
        return articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID())
                .designation("Article " + UUID.randomUUID())
                .prixUnitaireHt(new BigDecimal(prix))
                .tauxTva(new BigDecimal("19.25"))
                .category(category)
                .build()).getId();
    }

    private void approvisionner(Long id) {
        mvtStkService.entreeStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(id).build())
                .quantite(new BigDecimal("200"))
                .build());
    }

    // --- Au comptoir ---------------------------------------------------------------------

    @Test
    void une_vente_se_construit_article_par_article() {
        VenteDto vente = venteService.save(VenteDto.builder()
                .code("VTE-" + UUID.randomUUID())
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal("2"))
                        .prixUnitaire(new BigDecimal("500"))
                        .build()))
                .build());

        venteService.ajouterLigne(vente.getId(), LigneVenteDto.builder()
                .article(ArticleDto.builder().Id(idAutreArticle).build())
                .quantite(new BigDecimal("3"))
                .prixUnitaire(new BigDecimal("1200"))
                .build());

        assertThat(venteService.lignes(vente.getId())).hasSize(2);
        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("198");
        assertThat(mvtStkService.stockReelArticle(idAutreArticle)).isEqualByComparingTo("197");
    }

    @Test
    void un_ajout_que_le_stock_ne_couvre_pas_est_refuse_et_ne_laisse_rien() {
        VenteDto vente = venteService.save(VenteDto.builder()
                .code("VTE-" + UUID.randomUUID())
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal("1"))
                        .prixUnitaire(new BigDecimal("500"))
                        .build()))
                .build());

        assertThatThrownBy(() -> venteService.ajouterLigne(vente.getId(), LigneVenteDto.builder()
                .article(ArticleDto.builder().Id(idAutreArticle).build())
                .quantite(new BigDecimal("9999"))
                .build()))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("Stock insuffisant");

        assertThat(venteService.lignes(vente.getId())).hasSize(1);
        assertThat(mvtStkService.stockReelArticle(idAutreArticle)).isEqualByComparingTo("200");
    }

    @Test
    void on_n_ajoute_rien_a_une_vente_facturee() {
        VenteDto vente = venteService.save(VenteDto.builder()
                .code("VTE-" + UUID.randomUUID())
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal("1"))
                        .prixUnitaire(new BigDecimal("500"))
                        .build()))
                .build());
        factureService.emettre(vente.getId());

        assertThatThrownBy(() -> venteService.ajouterLigne(vente.getId(), LigneVenteDto.builder()
                .article(ArticleDto.builder().Id(idAutreArticle).build())
                .quantite(BigDecimal.ONE)
                .build()))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("facturée");
    }

    // --- Sur commande client -------------------------------------------------------------

    private CommandeClientDto commandeValidee(ClientDto client, String quantite) {
        CommandeClientDto commande = commandeClientService.save(CommandeClientDto.builder()
                .code("CC-" + UUID.randomUUID())
                .client(client)
                .ligneCmndeClients(List.of(LigneCommandeClientDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal(quantite))
                        .prixUnitaire(new BigDecimal("500"))
                        .build()))
                .build());
        commandeClientService.mettreAJourEtat(commande.getId(), EtatCommande.VALIDEE);
        return commande;
    }

    private ClientDto client() {
        return clientService.save(ClientDto.builder()
                .nom("Mballa")
                .prenoms("Jeanne")
                .mail("jeanne" + UUID.randomUUID() + "@exemple.test")
                .numTel("690000000")
                .build());
    }

    @Test
    void servir_une_commande_cree_la_vente_et_sort_le_stock() {
        CommandeClientDto commande = commandeValidee(client(), "10");

        VenteDto vente = venteService.servirCommandeClient(commande.getId());

        assertThat(vente.getIdCommandeClient()).isEqualTo(commande.getId());
        assertThat(venteService.lignes(vente.getId())).hasSize(1);
        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("190");
        // La commande est servie : elle passe livree, et se fige.
        assertThat(commandeClientService.findById(commande.getId()).getEtat()).isEqualTo(EtatCommande.LIVREE);
    }

    @Test
    void la_facture_d_une_vente_sur_commande_porte_le_client() {
        CommandeClientDto commande = commandeValidee(client(), "4");
        VenteDto vente = venteService.servirCommandeClient(commande.getId());

        FactureDto facture = factureService.emettre(vente.getId());

        assertThat(facture.getNomClient()).isEqualTo("Mballa Jeanne");
        assertThat(facture.getIdClient()).isEqualTo(commande.getClient().getId());
    }

    @Test
    void une_vente_au_comptoir_se_fait_au_nom_d_un_client_sans_passer_par_une_commande() {
        ClientDto client = client();

        VenteDto vente = venteService.save(VenteDto.builder()
                .code("VTE-" + UUID.randomUUID())
                .client(client)
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(BigDecimal.ONE)
                        .prixUnitaire(new BigDecimal("500"))
                        .build()))
                .build());

        assertThat(vente.getClient().getId()).isEqualTo(client.getId());
        assertThat(factureService.emettre(vente.getId()).getNomClient()).isEqualTo("Mballa Jeanne");
    }

    @Test
    void le_client_s_attribue_apres_coup_tant_que_la_vente_n_est_pas_facturee() {
        VenteDto vente = venteService.save(VenteDto.builder()
                .code("VTE-" + UUID.randomUUID())
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(BigDecimal.ONE)
                        .prixUnitaire(new BigDecimal("500"))
                        .build()))
                .build());
        ClientDto client = client();

        // Le client se fait connaitre au moment de payer : la vente etait deja ouverte.
        VenteDto nommee = venteService.attribuerClient(vente.getId(), client.getId());
        assertThat(nommee.getClient().getId()).isEqualTo(client.getId());

        factureService.emettre(vente.getId());
        assertThatThrownBy(() -> venteService.attribuerClient(vente.getId(), client.getId()))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("facturée");
    }

    @Test
    void la_facture_d_une_vente_au_comptoir_reste_anonyme() {
        VenteDto vente = venteService.save(VenteDto.builder()
                .code("VTE-" + UUID.randomUUID())
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(BigDecimal.ONE)
                        .prixUnitaire(new BigDecimal("500"))
                        .build()))
                .build());

        FactureDto facture = factureService.emettre(vente.getId());

        // C'est le ticket de caisse : personne n'a donne son nom, et ce n'est pas une anomalie.
        assertThat(facture.getNomClient()).isNull();
        assertThat(facture.getIdClient()).isNull();
    }

    @Test
    void une_commande_non_validee_ne_se_sert_pas() {
        CommandeClientDto commande = commandeClientService.save(CommandeClientDto.builder()
                .code("CC-" + UUID.randomUUID())
                .client(client())
                .ligneCmndeClients(List.of(LigneCommandeClientDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal("2"))
                        .prixUnitaire(new BigDecimal("500"))
                        .build()))
                .build());

        assertThatThrownBy(() -> venteService.servirCommandeClient(commande.getId()))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("VALIDEE");

        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("200");
    }

    @Test
    void une_commande_ne_se_sert_qu_une_fois() {
        CommandeClientDto commande = commandeValidee(client(), "5");
        venteService.servirCommandeClient(commande.getId());

        // La commande est passee livree : elle ne peut plus etre servie, et la marchandise ne
        // sort donc pas deux fois.
        assertThatThrownBy(() -> venteService.servirCommandeClient(commande.getId()))
                .isInstanceOf(InvalidEntityException.class);

        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("195");
    }
}
