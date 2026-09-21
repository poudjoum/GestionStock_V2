package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeFourDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCmndeFournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCommandeClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneReceptionDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
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
 * La livraison partielle, des deux cotes.
 *
 * Une commande se soldait d'un coup : recevoir 6 unites sur 10 obligeait a mentir en declarant
 * tout livre, ou a ne rien enregistrer en attendant le reste — les deux faussent le stock.
 */
class LivraisonPartielleTest extends AbstractIntegrationTest {

    @Autowired
    private CommandeFourService commandeFourService;
    @Autowired
    private CommandeClientService commandeClientService;
    @Autowired
    private VenteService venteService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private FournisseurService fournisseurService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private MvtStkService mvtStkService;

    private Long idArticle;

    @BeforeEach
    void unArticle() {
        CategoryDto category = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID()).designation("Divers").build());
        idArticle = articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID())
                .designation("Article")
                .prixUnitaireHt(new BigDecimal("1000"))
                .category(category)
                .build()).getId();
    }

    private LigneReceptionDto reception(Long idLigne, String quantite) {
        LigneReceptionDto reception = new LigneReceptionDto();
        reception.setIdLigne(idLigne);
        reception.setQuantite(new BigDecimal(quantite));
        return reception;
    }

    // --- Commande fournisseur ---------------------------------------------------------------

    private CommandeFourDto commandeFournisseurDe(String quantite) {
        FournisseurDto fournisseur = fournisseurService.save(FournisseurDto.builder()
                .nom("Fournisseur " + UUID.randomUUID()).prenom("X")
                .mail(UUID.randomUUID() + "@exemple.test").tel("690000000").build());
        CommandeFourDto commande = commandeFourService.save(CommandeFourDto.builder()
                .code("CF-" + UUID.randomUUID())
                .fournisseur(fournisseur)
                .ligneCmndeFournisseur(List.of(LigneCmndeFournisseurDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal(quantite))
                        .prixUnitaire(new BigDecimal("800"))
                        .build()))
                .build());
        commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.VALIDEE);
        return commande;
    }

    @Test
    void recevoir_une_partie_fait_entrer_cette_partie_et_rien_de_plus() {
        CommandeFourDto commande = commandeFournisseurDe("10");
        Long idLigne = commandeFourService.lignes(commande.getId()).get(0).getId();

        CommandeFourDto apres = commandeFourService.recevoir(commande.getId(), List.of(reception(idLigne, "6")));

        assertThat(apres.getEtat()).isEqualTo(EtatCommande.PARTIELLEMENT_LIVREE);
        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("6");
        LigneCmndeFournisseurDto ligne = commandeFourService.lignes(commande.getId()).get(0);
        assertThat(ligne.getQuantiteLivree()).isEqualByComparingTo("6");
        assertThat(ligne.getResteALivrer()).isEqualByComparingTo("4");
    }

    @Test
    void le_reliquat_solde_la_commande() {
        CommandeFourDto commande = commandeFournisseurDe("10");
        Long idLigne = commandeFourService.lignes(commande.getId()).get(0).getId();
        commandeFourService.recevoir(commande.getId(), List.of(reception(idLigne, "6")));

        CommandeFourDto apres = commandeFourService.recevoir(commande.getId(), List.of(reception(idLigne, "4")));

        assertThat(apres.getEtat()).isEqualTo(EtatCommande.LIVREE);
        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("10");
        assertThat(commandeFourService.lignes(commande.getId()).get(0).getResteALivrer())
                .isEqualByComparingTo("0");
    }

    @Test
    void recevoir_plus_que_ce_qui_reste_attendu_est_refuse() {
        CommandeFourDto commande = commandeFournisseurDe("10");
        Long idLigne = commandeFourService.lignes(commande.getId()).get(0).getId();
        commandeFourService.recevoir(commande.getId(), List.of(reception(idLigne, "6")));

        // Ce n'est pas une livraison, c'est une erreur de comptage : le stock ne doit pas en
        // porter la trace en silence.
        assertThatThrownBy(() -> commandeFourService.recevoir(commande.getId(), List.of(reception(idLigne, "5"))))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("dépasse ce qui reste attendu");

        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("6");
    }

    @Test
    void declarer_la_commande_livree_recoit_tout_le_reliquat() {
        CommandeFourDto commande = commandeFournisseurDe("10");
        Long idLigne = commandeFourService.lignes(commande.getId()).get(0).getId();
        commandeFourService.recevoir(commande.getId(), List.of(reception(idLigne, "6")));

        // Le raccourci passe par la meme operation : un seul chemin ecrit le stock.
        commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.LIVREE);

        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("10");
        assertThat(commandeFourService.findById(commande.getId()).getEtat()).isEqualTo(EtatCommande.LIVREE);
    }

    @Test
    void une_commande_partiellement_livree_ne_se_corrige_plus() {
        CommandeFourDto commande = commandeFournisseurDe("10");
        Long idLigne = commandeFourService.lignes(commande.getId()).get(0).getId();
        commandeFourService.recevoir(commande.getId(), List.of(reception(idLigne, "6")));

        // Sinon le reste attendu ne voudrait plus rien dire.
        assertThatThrownBy(() -> commandeFourService.modifierQuantite(commande.getId(), idLigne, new BigDecimal("3")))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("ne se modifie plus");
    }

    @Test
    void une_commande_partiellement_livree_ne_s_annule_pas() {
        CommandeFourDto commande = commandeFournisseurDe("10");
        Long idLigne = commandeFourService.lignes(commande.getId()).get(0).getId();
        commandeFourService.recevoir(commande.getId(), List.of(reception(idLigne, "6")));

        // Du stock est deja entre : l'annuler laisserait des quantites sans commande pour les
        // expliquer.
        assertThatThrownBy(() -> commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.ANNULEE))
                .isInstanceOf(InvalidEntityException.class);
    }

    // --- Commande client --------------------------------------------------------------------

    private CommandeClientDto commandeClientDe(String quantite) {
        ClientDto client = clientService.save(ClientDto.builder()
                .nom("Client").prenoms("X")
                .mail(UUID.randomUUID() + "@exemple.test").numTel("690000000").build());
        CommandeClientDto commande = commandeClientService.save(CommandeClientDto.builder()
                .code("CC-" + UUID.randomUUID())
                .client(client)
                .ligneCmndeClients(List.of(LigneCommandeClientDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal(quantite))
                        .prixUnitaire(new BigDecimal("1000"))
                        .build()))
                .build());
        commandeClientService.mettreAJourEtat(commande.getId(), EtatCommande.VALIDEE);
        return commande;
    }

    @Test
    void servir_une_partie_de_la_commande_client_ne_sort_que_cette_partie() {
        mvtStkService.entreeStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(idArticle).build())
                .quantite(new BigDecimal("100")).build());
        CommandeClientDto commande = commandeClientDe("10");
        Long idLigne = commandeClientService.lignes(commande.getId()).get(0).getId();

        venteService.servirCommandeClient(commande.getId(), List.of(reception(idLigne, "3")));

        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("97");
        assertThat(commandeClientService.findById(commande.getId()).getEtat())
                .isEqualTo(EtatCommande.PARTIELLEMENT_LIVREE);
    }

    @Test
    void le_reste_du_client_part_quand_la_marchandise_arrive() {
        mvtStkService.entreeStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(idArticle).build())
                .quantite(new BigDecimal("100")).build());
        CommandeClientDto commande = commandeClientDe("10");
        Long idLigne = commandeClientService.lignes(commande.getId()).get(0).getId();
        venteService.servirCommandeClient(commande.getId(), List.of(reception(idLigne, "3")));

        // Sans precision, tout ce qui reste du : c'est le geste ordinaire.
        venteService.servirCommandeClient(commande.getId());

        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("90");
        assertThat(commandeClientService.findById(commande.getId()).getEtat())
                .isEqualTo(EtatCommande.LIVREE);
    }

    @Test
    void servir_plus_que_ce_qui_reste_du_est_refuse() {
        mvtStkService.entreeStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(idArticle).build())
                .quantite(new BigDecimal("100")).build());
        CommandeClientDto commande = commandeClientDe("10");
        Long idLigne = commandeClientService.lignes(commande.getId()).get(0).getId();
        venteService.servirCommandeClient(commande.getId(), List.of(reception(idLigne, "8")));

        assertThatThrownBy(() -> venteService.servirCommandeClient(commande.getId(),
                List.of(reception(idLigne, "5"))))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("dépasse ce qui reste dû");

        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("92");
    }

    @Test
    void une_commande_entierement_servie_n_a_plus_rien_a_servir() {
        mvtStkService.entreeStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(idArticle).build())
                .quantite(new BigDecimal("100")).build());
        CommandeClientDto commande = commandeClientDe("10");
        venteService.servirCommandeClient(commande.getId());

        assertThatThrownBy(() -> venteService.servirCommandeClient(commande.getId()))
                .isInstanceOf(InvalidEntityException.class);
    }
}
