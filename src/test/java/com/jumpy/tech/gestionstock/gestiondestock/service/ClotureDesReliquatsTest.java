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
 * La cloture d'un reliquat.
 *
 * Une commande partiellement livree dont le reste n'arrivera jamais restait bloquee dans cet
 * etat : elle figurait indefiniment parmi les commandes en cours, et rien ne permettait de la
 * solder. La clore n'est pas la declarer livree — c'est cesser d'attendre.
 */
class ClotureDesReliquatsTest extends AbstractIntegrationTest {

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

    /** Six unites sur dix recues, la commande attend encore quatre. */
    private CommandeFourDto commandeFournisseurEnReliquat() {
        CommandeFourDto commande = commandeFournisseurDe("10");
        Long idLigne = commandeFourService.lignes(commande.getId()).get(0).getId();
        commandeFourService.recevoir(commande.getId(), List.of(reception(idLigne, "6")));
        return commande;
    }

    @Test
    void cloturer_solde_la_commande_sans_rien_faire_entrer_en_stock() {
        CommandeFourDto commande = commandeFournisseurEnReliquat();

        CommandeFourDto apres = commandeFourService.cloturer(commande.getId(),
                "Fournisseur en rupture, article arrêté");

        assertThat(apres.getEtat()).isEqualTo(EtatCommande.CLOTUREE);
        assertThat(apres.getMotifCloture()).isEqualTo("Fournisseur en rupture, article arrêté");
        // Clore, c'est constater une absence : les quatre unites jamais arrivees ne doivent pas
        // entrer en magasin au passage.
        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("6");
    }

    @Test
    void la_cloture_laisse_lisible_ce_qui_n_est_jamais_arrive() {
        CommandeFourDto commande = commandeFournisseurEnReliquat();

        commandeFourService.cloturer(commande.getId(), "Fournisseur défaillant");

        // Les lignes ne sont pas rabotees sur ce qui a ete recu : l'ecart est precisement ce que
        // le fournisseur n'a pas honore.
        LigneCmndeFournisseurDto ligne = commandeFourService.lignes(commande.getId()).get(0);
        assertThat(ligne.getQuantite()).isEqualByComparingTo("10");
        assertThat(ligne.getQuantiteLivree()).isEqualByComparingTo("6");
        assertThat(ligne.getResteALivrer()).isEqualByComparingTo("4");
    }

    @Test
    void une_commande_cloturee_ne_se_distingue_pas_d_une_livree_par_hasard() {
        CommandeFourDto commande = commandeFournisseurEnReliquat();
        commandeFourService.cloturer(commande.getId(), "Fournisseur défaillant");

        // Toute la raison d'etre d'un etat distinct : six mois plus tard, on doit pouvoir
        // separer celui qui a tout livre de celui qui a fait defaut.
        assertThat(commandeFourService.findById(commande.getId()).getEtat())
                .isEqualTo(EtatCommande.CLOTUREE)
                .isNotEqualTo(EtatCommande.LIVREE);
    }

    @Test
    void une_commande_cloturee_ne_recoit_plus_rien() {
        CommandeFourDto commande = commandeFournisseurEnReliquat();
        Long idLigne = commandeFourService.lignes(commande.getId()).get(0).getId();
        commandeFourService.cloturer(commande.getId(), "Fournisseur défaillant");

        assertThatThrownBy(() -> commandeFourService.recevoir(commande.getId(),
                List.of(reception(idLigne, "4"))))
                .isInstanceOf(InvalidEntityException.class);
        assertThatThrownBy(() -> commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.LIVREE))
                .isInstanceOf(InvalidEntityException.class);
        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("6");
    }

    @Test
    void cloturer_sans_motif_est_refuse() {
        CommandeFourDto commande = commandeFournisseurEnReliquat();

        // « On a clos » sans dire pourquoi ne sert a rien a celui qui relira l'historique.
        assertThatThrownBy(() -> commandeFourService.cloturer(commande.getId(), "   "))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("motif de clôture est obligatoire");

        assertThat(commandeFourService.findById(commande.getId()).getEtat())
                .isEqualTo(EtatCommande.PARTIELLEMENT_LIVREE);
    }

    @Test
    void une_commande_dont_rien_n_est_arrive_ne_se_cloture_pas() {
        CommandeFourDto commande = commandeFournisseurDe("10");

        // Elle n'a pas de reliquat : elle s'annule, ce qui ne laisse pas croire qu'une partie
        // de la marchandise est passee.
        assertThatThrownBy(() -> commandeFourService.cloturer(commande.getId(), "Plus besoin"))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("ne se clôture pas");
    }

    @Test
    void une_commande_entierement_livree_ne_se_cloture_pas() {
        CommandeFourDto commande = commandeFournisseurDe("10");
        commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.LIVREE);

        assertThatThrownBy(() -> commandeFourService.cloturer(commande.getId(), "Pour voir"))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("ne se clôture pas");
    }

    @Test
    void une_commande_cloturee_ne_se_cloture_pas_deux_fois() {
        CommandeFourDto commande = commandeFournisseurEnReliquat();
        commandeFourService.cloturer(commande.getId(), "Fournisseur défaillant");

        // Sans quoi le second motif effacerait le premier, qui etait le vrai.
        assertThatThrownBy(() -> commandeFourService.cloturer(commande.getId(), "Autre raison"))
                .isInstanceOf(InvalidEntityException.class);
        assertThat(commandeFourService.findById(commande.getId()).getMotifCloture())
                .isEqualTo("Fournisseur défaillant");
    }

    @Test
    void on_ne_cloture_pas_en_declarant_l_etat() {
        CommandeFourDto commande = commandeFournisseurEnReliquat();

        // Par la route des etats, la cloture arriverait sans motif : l'historique dirait qu'on
        // n'attend plus rien, sans dire pourquoi.
        assertThatThrownBy(() -> commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.CLOTUREE))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("avec son motif");

        assertThat(commandeFourService.findById(commande.getId()).getEtat())
                .isEqualTo(EtatCommande.PARTIELLEMENT_LIVREE);
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

    /** Trois unites sur dix servies, le client en attend encore sept. */
    private CommandeClientDto commandeClientEnReliquat() {
        mvtStkService.entreeStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(idArticle).build())
                .quantite(new BigDecimal("100")).build());
        CommandeClientDto commande = commandeClientDe("10");
        Long idLigne = commandeClientService.lignes(commande.getId()).get(0).getId();
        venteService.servirCommandeClient(commande.getId(), List.of(reception(idLigne, "3")));
        return commande;
    }

    @Test
    void cloturer_une_commande_client_ne_remet_rien_en_magasin() {
        CommandeClientDto commande = commandeClientEnReliquat();

        CommandeClientDto apres = commandeClientService.cloturer(commande.getId(),
                "Client désisté");

        assertThat(apres.getEtat()).isEqualTo(EtatCommande.CLOTUREE);
        assertThat(apres.getMotifCloture()).isEqualTo("Client désisté");
        // Les trois unites servies sont parties pour de bon ; les sept autres n'ont jamais
        // quitte le magasin. Ni retour, ni sortie supplementaire.
        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("97");
    }

    @Test
    void la_cloture_cliente_laisse_lisible_ce_qui_n_a_pas_ete_servi() {
        CommandeClientDto commande = commandeClientEnReliquat();

        commandeClientService.cloturer(commande.getId(), "Client désisté");

        LigneCommandeClientDto ligne = commandeClientService.lignes(commande.getId()).get(0);
        assertThat(ligne.getQuantite()).isEqualByComparingTo("10");
        assertThat(ligne.getQuantiteLivree()).isEqualByComparingTo("3");
        assertThat(ligne.getResteAServir()).isEqualByComparingTo("7");
    }

    @Test
    void une_commande_client_cloturee_ne_se_sert_plus() {
        CommandeClientDto commande = commandeClientEnReliquat();
        commandeClientService.cloturer(commande.getId(), "Client désisté");

        assertThatThrownBy(() -> venteService.servirCommandeClient(commande.getId()))
                .isInstanceOf(InvalidEntityException.class);
        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("97");
    }

    @Test
    void une_commande_client_cloturee_ne_se_corrige_plus() {
        CommandeClientDto commande = commandeClientEnReliquat();
        Long idLigne = commandeClientService.lignes(commande.getId()).get(0).getId();
        commandeClientService.cloturer(commande.getId(), "Client désisté");

        assertThatThrownBy(() -> commandeClientService.modifierQuantite(commande.getId(), idLigne,
                new BigDecimal("3")))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("ne se modifie plus");
    }

    @Test
    void une_commande_client_sans_reliquat_ne_se_cloture_pas() {
        CommandeClientDto commande = commandeClientDe("10");

        assertThatThrownBy(() -> commandeClientService.cloturer(commande.getId(), "Plus besoin"))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("ne se clôture pas");
    }

    @Test
    void on_ne_cloture_pas_une_commande_client_en_declarant_l_etat() {
        CommandeClientDto commande = commandeClientEnReliquat();

        assertThatThrownBy(() -> commandeClientService.mettreAJourEtat(commande.getId(), EtatCommande.CLOTUREE))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("avec son motif");
    }

    @Test
    void cloturer_une_commande_client_sans_motif_est_refuse() {
        CommandeClientDto commande = commandeClientEnReliquat();

        assertThatThrownBy(() -> commandeClientService.cloturer(commande.getId(), null))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("motif de clôture est obligatoire");
    }
}
