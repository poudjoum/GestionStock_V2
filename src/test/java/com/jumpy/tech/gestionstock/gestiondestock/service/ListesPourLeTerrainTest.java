package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeFourDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCmndeFournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les listes telles que le terrain les demande.
 *
 * Deux ecrans mobiles les ont fait apparaitre. Un magasinier debout dans les rayons cherche un
 * article, il ne feuillette pas l'inventaire ; celui qui decharge un camion cherche les commandes
 * qu'il peut recevoir, pas l'historique des achats de la maison.
 */
class ListesPourLeTerrainTest extends AbstractIntegrationTest {

    @Autowired
    private StockService stockService;
    @Autowired
    private CommandeFourService commandeFourService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private FournisseurService fournisseurService;
    @Autowired
    private MvtStkService mvtStkService;

    private String suffixe;
    private Long idCategorie;

    @BeforeEach
    void unCatalogue() {
        suffixe = UUID.randomUUID().toString().substring(0, 8);
        idCategorie = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + suffixe).designation("Divers").build()).getId();
    }

    private Long article(String code, String designation) {
        return articleService.save(ArticleDto.builder()
                .codeArticle(code + "-" + suffixe)
                .designation(designation + " " + suffixe)
                .prixUnitaireHt(new BigDecimal("1000"))
                .category(CategoryDto.builder().Id(idCategorie).build())
                .build()).getId();
    }

    // --- L'inventaire, filtrable ------------------------------------------------------------

    @Test
    void chercher_un_article_dans_l_inventaire() {
        Long ciment = article("CIM", "Sac de ciment");
        article("TOL", "Tôle ondulée");
        mvtStkService.entreeStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(ciment).build())
                .quantite(new BigDecimal("7")).build());

        var trouves = stockService.inventaire("ciment " + suffixe, PageRequest.of(0, 20));

        assertThat(trouves.getContent()).hasSize(1);
        assertThat(trouves.getContent().get(0).getQuantite()).isEqualByComparingTo("7");
    }

    @Test
    void l_inventaire_sans_filtre_rend_toujours_tout() {
        article("CIM", "Sac de ciment");
        article("TOL", "Tôle ondulée");

        long avecVide = stockService.inventaire("", PageRequest.of(0, 500)).getTotalElements();
        long sansRien = stockService.inventaire(null, PageRequest.of(0, 500)).getTotalElements();

        assertThat(avecVide).isEqualTo(sansRien).isGreaterThanOrEqualTo(2);
    }

    @Test
    void l_inventaire_filtre_garde_ses_valorisations() {
        Long ciment = article("CIM", "Sac de ciment");
        mvtStkService.entreeStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(ciment).build())
                .quantite(new BigDecimal("4")).build());

        var ligne = stockService.inventaire("cim-" + suffixe, PageRequest.of(0, 20))
                .getContent().get(0);

        // Le filtre ne doit pas court-circuiter le calcul : c'est la meme page d'inventaire,
        // simplement plus courte.
        assertThat(ligne.getPrixUnitaireVente()).isEqualByComparingTo("1000");
        assertThat(ligne.getValeurAuPrixDeVente()).isEqualByComparingTo("4000");
        assertThat(ligne.getStatut()).isNotNull();
    }

    // --- Les commandes a recevoir ------------------------------------------------------------

    private CommandeFourDto commande(String code, EtatCommande etatVise) {
        FournisseurDto fournisseur = fournisseurService.save(FournisseurDto.builder()
                .nom("Cimenterie " + suffixe).prenom("X")
                .mail(UUID.randomUUID() + "@exemple.test").tel("690000000").build());
        CommandeFourDto commande = commandeFourService.save(CommandeFourDto.builder()
                .code(code + "-" + suffixe)
                .fournisseur(fournisseur)
                .ligneCmndeFournisseur(List.of(LigneCmndeFournisseurDto.builder()
                        .article(ArticleDto.builder().Id(article("ART" + code, "Article")).build())
                        .quantite(new BigDecimal("10"))
                        .prixUnitaire(new BigDecimal("800"))
                        .build()))
                .build());
        if (etatVise != EtatCommande.EN_PREPARATION) {
            commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.VALIDEE);
        }
        return commande;
    }

    @Test
    void le_quai_ne_voit_que_les_commandes_qu_il_peut_recevoir() {
        commande("ATT", EtatCommande.EN_PREPARATION);
        CommandeFourDto validee = commande("VAL", EtatCommande.VALIDEE);

        var aRecevoir = commandeFourService.rechercher(
                List.of(EtatCommande.VALIDEE, EtatCommande.PARTIELLEMENT_LIVREE),
                suffixe, PageRequest.of(0, 20));

        // Une commande encore en preparation n'a pas ete passee au fournisseur : rien ne peut en
        // arriver, et la montrer au quai ne ferait qu'encombrer.
        assertThat(aRecevoir.getContent()).hasSize(1);
        assertThat(aRecevoir.getContent().get(0).getId()).isEqualTo(validee.getId());
    }

    @Test
    void une_commande_partiellement_livree_reste_au_quai() {
        CommandeFourDto commande = commande("PAR", EtatCommande.VALIDEE);
        Long idLigne = commandeFourService.lignes(commande.getId()).get(0).getId();
        var reception = new com.jumpy.tech.gestionstock.gestiondestock.dto.LigneReceptionDto();
        reception.setIdLigne(idLigne);
        reception.setQuantite(new BigDecimal("4"));
        commandeFourService.recevoir(commande.getId(), List.of(reception));

        var aRecevoir = commandeFourService.rechercher(
                List.of(EtatCommande.VALIDEE, EtatCommande.PARTIELLEMENT_LIVREE),
                suffixe, PageRequest.of(0, 20));

        // Le reliquat est encore attendu : c'est tout l'interet de la livraison partielle.
        assertThat(aRecevoir.getContent()).extracting(CommandeFourDto::getId)
                .contains(commande.getId());
    }

    @Test
    void chercher_une_commande_par_son_code_ou_son_fournisseur() {
        CommandeFourDto commande = commande("BON", EtatCommande.VALIDEE);

        // Ce qui figure sur le bon de livraison qu'on a en main.
        assertThat(commandeFourService.rechercher(null, "bon-" + suffixe, PageRequest.of(0, 20))
                .getContent()).extracting(CommandeFourDto::getId).containsExactly(commande.getId());
        assertThat(commandeFourService.rechercher(null, "cimenterie " + suffixe, PageRequest.of(0, 20))
                .getContent()).extracting(CommandeFourDto::getId).contains(commande.getId());
    }

    @Test
    void sans_etat_demande_toutes_les_commandes_reviennent() {
        commande("UNE", EtatCommande.EN_PREPARATION);
        commande("DEU", EtatCommande.VALIDEE);

        // Ne pas filtrer est une demande legitime, pas une absence de demande.
        assertThat(commandeFourService.rechercher(null, suffixe, PageRequest.of(0, 20))
                .getTotalElements()).isEqualTo(2);
        assertThat(commandeFourService.rechercher(List.of(), suffixe, PageRequest.of(0, 20))
                .getTotalElements()).isEqualTo(2);
    }

    @Test
    void la_liste_des_commandes_est_paginee() {
        for (int i = 0; i < 3; i++) {
            commande("P" + i, EtatCommande.VALIDEE);
        }

        var page = commandeFourService.rechercher(null, suffixe, PageRequest.of(0, 2));

        // `findAll` les rendait toutes d'un bloc : tenable sur quelques dizaines de lignes, pas
        // sur un telephone au bout de trois ans d'exploitation.
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }
}
