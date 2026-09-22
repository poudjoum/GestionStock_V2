package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeFourDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EtatDuStockDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCmndeFournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneInventaireDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneReceptionDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;
import com.jumpy.tech.gestionstock.gestiondestock.entities.StatutStock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L'etat du magasin : ce qu'il vaut, et ce qui y manque.
 *
 * Le stock se lisait article par article ; personne ne pouvait dire ce que valait l'ensemble, ni
 * quels articles s'epuisaient — la rupture se decouvrait au comptoir, devant le client.
 */
class EtatDuStockTest extends AbstractIntegrationTest {

    @Autowired
    private StockService stockService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private EntrepriseService entrepriseService;
    @Autowired
    private FournisseurService fournisseurService;
    @Autowired
    private CommandeFourService commandeFourService;
    @Autowired
    private MvtStkService mvtStkService;

    private Long idEntreprise;
    private CategoryDto category;

    @BeforeEach
    void uneEntrepriseVierge() {
        // Chaque test travaille dans sa propre entreprise : l'etat du stock porte sur tout le
        // magasin, et les articles des autres tests le fausseraient.
        idEntreprise = entrepriseService.save(EntrepriseDto.builder()
                .nom("Entreprise " + UUID.randomUUID())
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .build()).getId();
        connecte();
        category = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID()).designation("Divers").build());
    }

    @AfterEach
    void oublierLUtilisateur() {
        SecurityContextHolder.clearContext();
    }

    private void connecte() {
        UserDetailsImpl principal = new UserDetailsImpl(1L, "gestionnaire", "g@exemple.test",
                "x", idEntreprise, List.of(new SimpleGrantedAuthority(ERole.ROLE_ADMIN.name())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Long article(String prixVente, String seuil) {
        return articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID())
                .designation("Article " + UUID.randomUUID())
                .prixUnitaireHt(new BigDecimal(prixVente))
                .seuilAlerte(seuil == null ? null : new BigDecimal(seuil))
                .category(category)
                .build()).getId();
    }

    private void approvisionnerALaMain(Long idArticle, String quantite) {
        mvtStkService.entreeStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(idArticle).build())
                .quantite(new BigDecimal(quantite)).build());
    }

    /** Achete puis livre : c'est ce qui donne un cout d'achat a l'article. */
    private void acheterEtLivrer(Long idArticle, String quantite, String prixAchat) {
        FournisseurDto fournisseur = fournisseurService.save(FournisseurDto.builder()
                .nom("Fournisseur " + UUID.randomUUID()).prenom("X")
                .mail(UUID.randomUUID() + "@exemple.test").tel("690000000").build());
        CommandeFourDto commande = commandeFourService.save(CommandeFourDto.builder()
                .code("CF-" + UUID.randomUUID())
                .fournisseur(fournisseur)
                .ligneCmndeFournisseur(List.of(LigneCmndeFournisseurDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal(quantite))
                        .prixUnitaire(new BigDecimal(prixAchat))
                        .build()))
                .build());
        commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.VALIDEE);
        commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.LIVREE);
    }

    @Test
    void le_stock_est_valorise_au_cout_d_achat_et_au_prix_de_vente() {
        Long idArticle = article("1500", null);
        acheterEtLivrer(idArticle, "10", "1000");

        EtatDuStockDto etat = stockService.etat();

        // 10 x 1000 achetes, 10 x 1500 s'ils se vendent : deux chiffres, deux questions.
        assertThat(etat.getValeurAuCout()).isEqualByComparingTo("10000.00");
        assertThat(etat.getValeurAuPrixDeVente()).isEqualByComparingTo("15000.00");
        assertThat(etat.getNombreArticles()).isEqualTo(1);
        assertThat(etat.getNombreSansCoutConnu()).isZero();
    }

    @Test
    void le_cout_est_moyen_et_non_celui_du_dernier_achat() {
        Long idArticle = article("3000", null);
        acheterEtLivrer(idArticle, "10", "1000");
        acheterEtLivrer(idArticle, "10", "2000");

        LigneInventaireDto ligne = stockService.inventaire(null, PageRequest.of(0, 10)).getContent().get(0);

        // 20 unites pour 30 000 : 1 500 l'unite. Retenir le dernier prix donnerait 40 000.
        assertThat(ligne.getCoutMoyenAchat()).isEqualByComparingTo("1500.00");
        assertThat(ligne.getValeurAuCout()).isEqualByComparingTo("30000.00");
    }

    @Test
    void un_article_jamais_achete_n_a_pas_de_cout_connu_et_le_dit() {
        Long idArticle = article("1500", null);
        approvisionnerALaMain(idArticle, "10");

        EtatDuStockDto etat = stockService.etat();
        LigneInventaireDto ligne = stockService.inventaire(null, PageRequest.of(0, 10)).getContent().get(0);

        // Mieux vaut avouer le cout inconnu que de l'inventer au prix de vente : sans ce compte,
        // une valorisation partielle passerait pour complete.
        assertThat(ligne.getCoutMoyenAchat()).isNull();
        assertThat(ligne.getValeurAuCout()).isNull();
        assertThat(etat.getNombreSansCoutConnu()).isEqualTo(1);
        assertThat(etat.getValeurAuCout()).isEqualByComparingTo("0.00");
        assertThat(etat.getValeurAuPrixDeVente()).isEqualByComparingTo("15000.00");
    }

    @Test
    void une_livraison_partielle_valorise_ce_qui_est_arrive() {
        Long idArticle = article("6000", null);
        FournisseurDto fournisseur = fournisseurService.save(FournisseurDto.builder()
                .nom("Fournisseur " + UUID.randomUUID()).prenom("X")
                .mail(UUID.randomUUID() + "@exemple.test").tel("690000000").build());
        CommandeFourDto commande = commandeFourService.save(CommandeFourDto.builder()
                .code("CF-" + UUID.randomUUID())
                .fournisseur(fournisseur)
                .ligneCmndeFournisseur(List.of(LigneCmndeFournisseurDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal("20"))
                        .prixUnitaire(new BigDecimal("4650"))
                        .build()))
                .build());
        commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.VALIDEE);
        Long idLigne = commandeFourService.lignes(commande.getId()).get(0).getId();
        LigneReceptionDto douze = new LigneReceptionDto();
        douze.setIdLigne(idLigne);
        douze.setQuantite(new BigDecimal("12"));
        commandeFourService.recevoir(commande.getId(), List.of(douze));

        LigneInventaireDto ligne = stockService.inventaire(null, PageRequest.of(0, 10)).getContent().get(0);

        // Douze sacs sont en magasin : ils valent ce qu'ils ont coute, pas rien. Le calcul ne
        // regardait que les commandes soldees, et le magasin comptait alors de la marchandise
        // valorisee a zero — l'ecart ne se voyait nulle part.
        assertThat(ligne.getQuantite()).isEqualByComparingTo("12");
        assertThat(ligne.getCoutMoyenAchat()).isEqualByComparingTo("4650.00");
        assertThat(ligne.getValeurAuCout()).isEqualByComparingTo("55800.00");
    }

    @Test
    void un_reliquat_cloture_ne_valorise_que_ce_qui_a_ete_recu() {
        Long idArticle = article("6000", null);
        FournisseurDto fournisseur = fournisseurService.save(FournisseurDto.builder()
                .nom("Fournisseur " + UUID.randomUUID()).prenom("X")
                .mail(UUID.randomUUID() + "@exemple.test").tel("690000000").build());
        CommandeFourDto commande = commandeFourService.save(CommandeFourDto.builder()
                .code("CF-" + UUID.randomUUID())
                .fournisseur(fournisseur)
                .ligneCmndeFournisseur(List.of(LigneCmndeFournisseurDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal("20"))
                        .prixUnitaire(new BigDecimal("4650"))
                        .build()))
                .build());
        commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.VALIDEE);
        Long idLigne = commandeFourService.lignes(commande.getId()).get(0).getId();
        LigneReceptionDto douze = new LigneReceptionDto();
        douze.setIdLigne(idLigne);
        douze.setQuantite(new BigDecimal("12"));
        commandeFourService.recevoir(commande.getId(), List.of(douze));
        commandeFourService.cloturer(commande.getId(), "fournisseur en rupture");

        LigneInventaireDto ligne = stockService.inventaire(null, PageRequest.of(0, 10)).getContent().get(0);

        // Clore, c'est cesser d'attendre les huit manquants — pas oublier les douze recus.
        assertThat(ligne.getValeurAuCout()).isEqualByComparingTo("55800.00");
    }

    @Test
    void une_commande_non_livree_ne_donne_pas_de_cout() {
        Long idArticle = article("1500", null);
        FournisseurDto fournisseur = fournisseurService.save(FournisseurDto.builder()
                .nom("Fournisseur " + UUID.randomUUID()).prenom("X")
                .mail(UUID.randomUUID() + "@exemple.test").tel("690000000").build());
        commandeFourService.save(CommandeFourDto.builder()
                .code("CF-" + UUID.randomUUID())
                .fournisseur(fournisseur)
                .ligneCmndeFournisseur(List.of(LigneCmndeFournisseurDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal("10"))
                        .prixUnitaire(new BigDecimal("1000"))
                        .build()))
                .build());

        // La marchandise n'est pas arrivee : son prix n'a encore rien coute.
        assertThat(stockService.etat().getNombreSansCoutConnu()).isEqualTo(1);
    }

    @Test
    void les_articles_sous_leur_seuil_et_en_rupture_sont_comptes() {
        Long enRupture = article("1000", "5");
        Long sousSeuil = article("1000", "10");
        Long suffisant = article("1000", "2");
        Long sansSeuil = article("1000", null);
        approvisionnerALaMain(sousSeuil, "8");
        approvisionnerALaMain(suffisant, "50");
        approvisionnerALaMain(sansSeuil, "3");

        EtatDuStockDto etat = stockService.etat();

        assertThat(etat.getNombreArticles()).isEqualTo(4);
        assertThat(etat.getNombreEnRupture()).isEqualTo(1);
        assertThat(etat.getNombreSousSeuil()).isEqualTo(1);
        assertThat(enRupture).isNotNull();
    }

    @Test
    void les_alertes_ne_rendent_que_ce_qu_il_faut_recommander() {
        Long enRupture = article("1000", "5");
        Long sousSeuil = article("1000", "10");
        Long suffisant = article("1000", "2");
        approvisionnerALaMain(sousSeuil, "8");
        approvisionnerALaMain(suffisant, "50");

        List<LigneInventaireDto> alertes = stockService.alertes();

        assertThat(alertes)
                .extracting(LigneInventaireDto::getIdArticle)
                .containsExactlyInAnyOrder(enRupture, sousSeuil);
        assertThat(alertes)
                .extracting(LigneInventaireDto::getStatut)
                .containsExactlyInAnyOrder(StatutStock.RUPTURE, StatutStock.SOUS_SEUIL);
    }

    @Test
    void un_article_sans_seuil_n_est_jamais_signale_tant_qu_il_reste_du_stock() {
        Long idArticle = article("1000", null);
        approvisionnerALaMain(idArticle, "1");

        LigneInventaireDto ligne = stockService.inventaire(null, PageRequest.of(0, 10)).getContent().get(0);

        // Sans seuil, on ne peut rien dire : le confondre avec « suffisant » ferait croire a une
        // surveillance qui n'existe pas.
        assertThat(ligne.getStatut()).isEqualTo(StatutStock.SANS_SEUIL);
        assertThat(stockService.alertes()).isEmpty();
    }

    @Test
    void un_magasin_vide_rend_des_zeros_et_non_une_absence_de_reponse() {
        EtatDuStockDto etat = stockService.etat();

        assertThat(etat.getNombreArticles()).isZero();
        assertThat(etat.getValeurAuCout()).isEqualByComparingTo("0.00");
        assertThat(etat.getValeurAuPrixDeVente()).isEqualByComparingTo("0.00");
        assertThat(stockService.alertes()).isEmpty();
    }
}
