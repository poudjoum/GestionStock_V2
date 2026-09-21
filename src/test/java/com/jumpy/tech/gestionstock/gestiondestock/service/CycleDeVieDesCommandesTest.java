package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeFourDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCmndeFournisseurDto;
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
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;

/**
 * Le cycle de vie des commandes fournisseur, et son effet sur le stock.
 *
 * Avant ce cycle, enregistrer une commande faisait entrer la marchandise : le stock montait avant
 * que le camion n'arrive. C'est desormais la livraison qui l'alimente.
 */
class CycleDeVieDesCommandesTest extends AbstractIntegrationTest {

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

    private Long idArticle;
    private FournisseurDto fournisseur;

    @BeforeEach
    void preparer() {
        CategoryDto category = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID())
                .designation("Fournitures")
                .build());

        idArticle = articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID())
                .designation("Ramette A4")
                .prixUnitaireHt(new BigDecimal("3000"))
                .tauxTva(new BigDecimal("19.25"))
                .category(category)
                .build()).getId();

        fournisseur = fournisseurService.save(FournisseurDto.builder()
                .nom("Papeterie " + UUID.randomUUID())
                .prenom("Centrale")
                .mail("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .build());
    }

    private CommandeFourDto commanderDe(String quantite) {
        return commandeFourService.save(CommandeFourDto.builder()
                .code("CF-" + UUID.randomUUID())
                .fournisseur(fournisseur)
                .ligneCmndeFournisseur(List.of(LigneCmndeFournisseurDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal(quantite))
                        .prixUnitaire(new BigDecimal("3000"))
                        .build()))
                .build());
    }

    @Test
    void une_commande_nait_en_preparation_et_ne_touche_pas_au_stock() {
        CommandeFourDto commande = commanderDe("50");

        assertThat(commande.getEtat()).isEqualTo(EtatCommande.EN_PREPARATION);
        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("0");
    }

    @Test
    void la_livraison_fait_entrer_la_marchandise() {
        CommandeFourDto commande = commanderDe("50");

        commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.VALIDEE);
        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("0");

        CommandeFourDto livree = commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.LIVREE);

        assertThat(livree.getEtat()).isEqualTo(EtatCommande.LIVREE);
        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("50");
    }

    @Test
    void une_commande_livree_ne_peut_plus_changer_d_etat() {
        CommandeFourDto commande = commanderDe("50");
        commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.VALIDEE);
        commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.LIVREE);

        // C'est ce refus qui empeche la marchandise d'entrer deux fois : une seconde livraison
        // relirait les memes lignes et doublerait le stock.
        assertThatThrownBy(() -> commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.LIVREE))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("ne peut pas passer");

        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("50");
    }

    @Test
    void une_commande_livree_ne_s_annule_pas() {
        CommandeFourDto commande = commanderDe("20");
        commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.VALIDEE);
        commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.LIVREE);

        // La marchandise a bouge : l'annuler laisserait le stock mentir. Le motif du refus est
        // porte par le detail, le message se contentant d'annoncer la transition refusee.
        assertThatThrownBy(() -> commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.ANNULEE))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("ne peut pas passer")
                .asInstanceOf(throwable(InvalidEntityException.class))
                .extracting(InvalidEntityException::getErrors, list(String.class))
                .anySatisfy(detail -> assertThat(detail).contains("définitif"));
    }

    @Test
    void une_commande_annulee_n_entre_jamais_en_stock() {
        CommandeFourDto commande = commanderDe("30");

        commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.ANNULEE);

        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("0");
        assertThatThrownBy(() -> commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.LIVREE))
                .isInstanceOf(InvalidEntityException.class);
        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("0");
    }

    @Test
    void on_ne_saute_pas_de_la_preparation_a_la_livraison() {
        CommandeFourDto commande = commanderDe("10");

        assertThatThrownBy(() -> commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.LIVREE))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("EN_PREPARATION");

        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("0");
    }

    @Test
    void l_etat_vise_doit_etre_renseigne() {
        CommandeFourDto commande = commanderDe("10");

        assertThatThrownBy(() -> commandeFourService.mettreAJourEtat(commande.getId(), null))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("renseigné");
    }
}
