package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeFourDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCmndeFournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
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
 * La correction d'une commande : ajouter, retirer, changer une quantite.
 *
 * Rien de tout cela n'existait — une commande enregistree etait definitive. Ces operations
 * n'ecrivent aucun mouvement de stock et n'ont rien a rattraper : la marchandise n'entre qu'a la
 * livraison, qui relit les lignes telles qu'elles sont a ce moment-la.
 */
class ModificationDesCommandesTest extends AbstractIntegrationTest {

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
    private Long idAutreArticle;
    private CommandeFourDto commande;

    @BeforeEach
    void preparer() {
        CategoryDto category = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID())
                .designation("Fournitures")
                .build());

        idArticle = creerArticle(category);
        idAutreArticle = creerArticle(category);

        FournisseurDto fournisseur = fournisseurService.save(FournisseurDto.builder()
                .nom("Grossiste " + UUID.randomUUID())
                .prenom("Central")
                .mail("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .build());

        commande = commandeFourService.save(CommandeFourDto.builder()
                .code("CF-" + UUID.randomUUID())
                .fournisseur(fournisseur)
                .ligneCmndeFournisseur(List.of(LigneCmndeFournisseurDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal("10"))
                        .prixUnitaire(new BigDecimal("1000"))
                        .build()))
                .build());
    }

    private Long creerArticle(CategoryDto category) {
        return articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID())
                .designation("Article de test")
                .prixUnitaireHt(new BigDecimal("1000"))
                .tauxTva(new BigDecimal("19.25"))
                .category(category)
                .build()).getId();
    }

    private LigneCmndeFournisseurDto ligneAjoutee(Long idArticleVise, String quantite) {
        return commandeFourService.ajouterLigne(commande.getId(), LigneCmndeFournisseurDto.builder()
                .article(ArticleDto.builder().Id(idArticleVise).build())
                .quantite(new BigDecimal(quantite))
                .prixUnitaire(new BigDecimal("1000"))
                .build());
    }

    @Test
    void une_ligne_s_ajoute_a_une_commande_en_preparation() {
        ligneAjoutee(idAutreArticle, "7");

        assertThat(commandeFourService.lignes(commande.getId())).hasSize(2);
    }

    @Test
    void la_quantite_d_une_ligne_se_corrige() {
        Long idLigne = commandeFourService.lignes(commande.getId()).get(0).getId();

        LigneCmndeFournisseurDto corrigee =
                commandeFourService.modifierQuantite(commande.getId(), idLigne, new BigDecimal("25"));

        assertThat(corrigee.getQuantite()).isEqualByComparingTo("25");
    }

    @Test
    void le_prix_d_achat_d_une_ligne_se_corrige() {
        Long idLigne = commandeFourService.lignes(commande.getId()).get(0).getId();

        // Le tarif du fournisseur arrive souvent apres la saisie de la commande. Sans cette
        // correction, il fallait retirer la ligne et la recreer — et le prix d'achat decide du
        // cout moyen de l'article, donc de la valeur du magasin.
        LigneCmndeFournisseurDto corrigee =
                commandeFourService.modifierLigne(commande.getId(), idLigne, null, new BigDecimal("1250"));

        assertThat(corrigee.getPrixUnitaire()).isEqualByComparingTo("1250");
        assertThat(corrigee.getQuantite()).isEqualByComparingTo("10");
    }

    @Test
    void une_modification_qui_ne_demande_rien_est_refusee() {
        Long idLigne = commandeFourService.lignes(commande.getId()).get(0).getId();

        assertThatThrownBy(() -> commandeFourService.modifierLigne(commande.getId(), idLigne, null, null))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("ce qu'il faut changer");
    }

    @Test
    void un_prix_d_achat_negatif_est_refuse() {
        Long idLigne = commandeFourService.lignes(commande.getId()).get(0).getId();

        assertThatThrownBy(() ->
                commandeFourService.modifierLigne(commande.getId(), idLigne, null, new BigDecimal("-1")))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("négatif");
    }

    @Test
    void une_ligne_se_retire() {
        Long idLigne = commandeFourService.lignes(commande.getId()).get(0).getId();

        commandeFourService.retirerLigne(commande.getId(), idLigne);

        assertThat(commandeFourService.lignes(commande.getId())).isEmpty();
    }

    @Test
    void la_livraison_prend_les_lignes_telles_qu_elles_sont_au_moment_ou_elle_a_lieu() {
        Long idLigne = commandeFourService.lignes(commande.getId()).get(0).getId();
        commandeFourService.modifierQuantite(commande.getId(), idLigne, new BigDecimal("30"));
        ligneAjoutee(idAutreArticle, "5");

        commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.VALIDEE);
        commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.LIVREE);

        // 30 et non 10 : c'est la quantite corrigee qui entre, pas celle de la saisie initiale.
        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("30");
        assertThat(mvtStkService.stockReelArticle(idAutreArticle)).isEqualByComparingTo("5");
    }

    @Test
    void une_commande_livree_ne_se_modifie_plus() {
        Long idLigne = commandeFourService.lignes(commande.getId()).get(0).getId();
        commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.VALIDEE);
        commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.LIVREE);

        // Ses lignes ont fait entrer la marchandise : les toucher ferait mentir le stock.
        assertThatThrownBy(() -> commandeFourService.modifierQuantite(commande.getId(), idLigne, new BigDecimal("99")))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("ne se modifie plus");

        assertThatThrownBy(() -> commandeFourService.retirerLigne(commande.getId(), idLigne))
                .isInstanceOf(InvalidEntityException.class);

        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("10");
    }

    @Test
    void une_quantite_nulle_ou_negative_est_refusee() {
        Long idLigne = commandeFourService.lignes(commande.getId()).get(0).getId();

        assertThatThrownBy(() -> commandeFourService.modifierQuantite(commande.getId(), idLigne, new BigDecimal("0")))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("strictement positive");

        assertThatThrownBy(() -> ligneAjoutee(idAutreArticle, "-3"))
                .isInstanceOf(InvalidEntityException.class);
    }

    @Test
    void une_ligne_d_une_autre_commande_ne_se_modifie_pas_par_ce_chemin() {
        Long idLigne = commandeFourService.lignes(commande.getId()).get(0).getId();
        CommandeFourDto autre = commandeFourService.save(CommandeFourDto.builder()
                .code("CF-" + UUID.randomUUID())
                .fournisseur(commande.getFournisseur())
                .build());

        // Connaitre un identifiant de ligne ne doit pas suffire a toucher la commande d'un autre.
        assertThatThrownBy(() -> commandeFourService.modifierQuantite(autre.getId(), idLigne, new BigDecimal("5")))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("n'appartient pas");
    }

    @Test
    void une_ligne_sur_un_article_inconnu_est_refusee() {
        assertThatThrownBy(() -> ligneAjoutee(999_999L, "3"))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
