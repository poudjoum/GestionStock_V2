package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneComptageDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.SeanceInventaireDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.MotifMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.entities.StatutSeanceInventaire;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * L'inventaire : confronter ce que le logiciel croit avoir a ce qu'il y a sur l'etagere.
 *
 * Le stock se deduisait de ses mouvements, et rien ne permettait de constater qu'il s'etait
 * trompe — la casse, le vol, la livraison mal saisie. Ces tests ouvrent une seance, comptent, et
 * verifient que le rattrapage passe par des mouvements ordinaires plutot que par une reecriture.
 *
 * Les articles sont crees sous une entreprise a eux : une seance fige tout le catalogue de son
 * entreprise, et sans ce cloisonnement elle embarquerait les articles de toute la campagne.
 */
class InventaireTest extends AbstractIntegrationTest {

    @Autowired
    private InventaireService inventaireService;
    @Autowired
    private EntrepriseService entrepriseService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private MvtStkService mvtStkService;
    @Autowired
    private VenteService venteService;

    private Long idCiment;
    private Long idFil;

    @BeforeEach
    void unMagasinDeDeuxArticles() {
        Long idEntreprise = entrepriseService.save(EntrepriseDto.builder()
                .nom("Quincaillerie " + UUID.randomUUID())
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .build()).getId();
        connecteChez(idEntreprise);

        CategoryDto categorie = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID())
                .designation("Matériaux")
                .build());
        idCiment = article("Ciment 50 kg", categorie, "10");
        idFil = article("Fil à plomb", categorie, "5");
    }

    @AfterEach
    void oublierLUtilisateur() {
        // Le contexte de securite est porte par le fil d'execution, qui est reutilise d'un test a
        // l'autre : sans ce nettoyage, le suivant heriterait de cet utilisateur.
        SecurityContextHolder.clearContext();
    }

    private void connecteChez(Long idEntreprise) {
        UserDetailsImpl principal = new UserDetailsImpl(1L, "gerant", "gerant@exemple.test",
                "x", idEntreprise, List.of(new SimpleGrantedAuthority(ERole.ROLE_ADMIN.name())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Long article(String designation, CategoryDto categorie, String stock) {
        Long id = articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID())
                .designation(designation)
                .prixUnitaireHt(new BigDecimal("1000"))
                .category(categorie)
                .build()).getId();
        mvtStkService.entreeStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(id).build())
                .quantite(new BigDecimal(stock))
                .build());
        return id;
    }

    private String codeDe(Long idArticle) {
        return articleService.findById(idArticle).getCodeArticle();
    }

    private LigneComptageDto ligneDe(Long idSeance, Long idArticle) {
        return inventaireService.lignes(idSeance, "", "TOUTES", PageRequest.of(0, 50))
                .getContent().stream()
                .filter(l -> l.idArticle().equals(idArticle))
                .findFirst().orElseThrow();
    }

    private BigDecimal stock(Long idArticle) {
        return mvtStkService.stockReelArticle(idArticle);
    }

    /** Une vente ordinaire, pour faire bouger le stock pendant qu'on compte. */
    private void vendre(Long idArticle, String quantite) {
        venteService.save(VenteDto.builder()
                .code("VTE-" + UUID.randomUUID())
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal(quantite))
                        .prixUnitaire(new BigDecimal("1000"))
                        .build()))
                .build());
    }

    @Test
    void ouvrir_inscrit_tout_le_catalogue_avec_son_stock() {
        SeanceInventaireDto seance = inventaireService.ouvrir("Fin de mois");

        assertThat(seance.statut()).isEqualTo(StatutSeanceInventaire.OUVERTE);
        assertThat(seance.reference()).startsWith("INV-");
        assertThat(seance.articles()).isEqualTo(2);
        assertThat(seance.comptes()).isZero();

        assertThat(ligneDe(seance.id(), idCiment).quantiteTheorique()).isEqualByComparingTo("10");
        assertThat(ligneDe(seance.id(), idFil).quantiteTheorique()).isEqualByComparingTo("5");
        // Pas encore regarde : ce n'est pas la meme chose que compte a zero.
        assertThat(ligneDe(seance.id(), idCiment).quantiteComptee()).isNull();
    }

    @Test
    void deux_seances_ne_s_ouvrent_pas_en_meme_temps() {
        inventaireService.ouvrir(null);

        // Deux comptages simultanes produiraient deux corrections dont la seconde defait la
        // premiere.
        assertThatThrownBy(() -> inventaireService.ouvrir(null))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("déjà ouverte");
    }

    @Test
    void compter_montre_l_ecart_sans_toucher_au_stock() {
        SeanceInventaireDto seance = inventaireService.ouvrir(null);

        LigneComptageDto ligne = inventaireService.compter(seance.id(), idCiment, new BigDecimal("8"));

        assertThat(ligne.quantiteComptee()).isEqualByComparingTo("8");
        assertThat(ligne.ecart()).isEqualByComparingTo("-2");
        // Rien ne bouge avant la validation : c'est tout l'interet de la seance.
        assertThat(stock(idCiment)).isEqualByComparingTo("10");
    }

    @Test
    void la_douchette_compte_par_le_code_barres() {
        SeanceInventaireDto seance = inventaireService.ouvrir(null);

        LigneComptageDto ligne = inventaireService.compterParCode(
                seance.id(), codeDe(idCiment), new BigDecimal("12"));

        assertThat(ligne.idArticle()).isEqualTo(idCiment);
        assertThat(ligne.ecart()).isEqualByComparingTo("2");
    }

    @Test
    void un_code_que_personne_ne_porte_est_signale() {
        SeanceInventaireDto seance = inventaireService.ouvrir(null);

        assertThatThrownBy(() -> inventaireService.compterParCode(
                seance.id(), "CODE-INEXISTANT", BigDecimal.ONE))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("CODE-INEXISTANT");
    }

    @Test
    void recompter_remplace_la_valeur_au_lieu_de_s_y_ajouter() {
        SeanceInventaireDto seance = inventaireService.ouvrir(null);

        inventaireService.compter(seance.id(), idCiment, new BigDecimal("8"));
        // On recompte une etagere quand on doute du premier passage, pas pour cumuler deux tas.
        LigneComptageDto ligne = inventaireService.compter(seance.id(), idCiment, new BigDecimal("9"));

        assertThat(ligne.quantiteComptee()).isEqualByComparingTo("9");
    }

    @Test
    void annuler_un_comptage_remet_la_ligne_a_non_comptee() {
        SeanceInventaireDto seance = inventaireService.ouvrir(null);
        LigneComptageDto comptee = inventaireService.compter(seance.id(), idCiment, BigDecimal.ZERO);
        assertThat(comptee.quantiteComptee()).isEqualByComparingTo("0");

        LigneComptageDto reprise = inventaireService.annulerComptage(seance.id(), comptee.id());

        // Zero compte disait « il n'y en a plus » ; nul dit « pas encore regarde ». Les deux
        // appellent des corrections opposees, et c'est pourquoi on peut revenir au second.
        assertThat(reprise.quantiteComptee()).isNull();
        assertThat(reprise.ecart()).isNull();
    }

    @Test
    void une_quantite_negative_est_refusee() {
        SeanceInventaireDto seance = inventaireService.ouvrir(null);

        assertThatThrownBy(() -> inventaireService.compter(seance.id(), idCiment, new BigDecimal("-1")))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("négative");
    }

    @Test
    void valider_rattrape_les_ecarts_par_des_mouvements_de_stock() {
        SeanceInventaireDto seance = inventaireService.ouvrir(null);
        inventaireService.compter(seance.id(), idCiment, new BigDecimal("8"));
        inventaireService.compter(seance.id(), idFil, new BigDecimal("7"));

        SeanceInventaireDto close = inventaireService.valider(seance.id());

        assertThat(close.statut()).isEqualTo(StatutSeanceInventaire.VALIDEE);
        assertThat(close.dateCloture()).isNotNull();
        assertThat(stock(idCiment)).isEqualByComparingTo("8");
        assertThat(stock(idFil)).isEqualByComparingTo("7");
    }

    @Test
    void la_correction_porte_le_motif_inventaire() {
        SeanceInventaireDto seance = inventaireService.ouvrir(null);
        inventaireService.compter(seance.id(), idCiment, new BigDecimal("8"));
        inventaireService.valider(seance.id());

        // Sans ce motif, la correction serait indiscernable d'une saisie manuelle — et l'on ne
        // pourrait plus repondre a la seule question qui vaille : d'ou vient cette unite.
        assertThat(mvtStkService.mvtStkArticle(idCiment))
                .anyMatch(m -> m.getMotif() == MotifMvtStk.INVENTAIRE);
    }

    @Test
    void un_article_qu_on_n_a_pas_regarde_ne_produit_aucune_correction() {
        SeanceInventaireDto seance = inventaireService.ouvrir(null);
        inventaireService.compter(seance.id(), idCiment, new BigDecimal("8"));

        inventaireService.valider(seance.id());

        // On ne deduit pas d'un rayon qu'on n'a pas compte qu'il est vide.
        assertThat(stock(idFil)).isEqualByComparingTo("5");
    }

    @Test
    void une_vente_passee_pendant_le_comptage_n_est_pas_effacee() {
        SeanceInventaireDto seance = inventaireService.ouvrir(null);

        // Le magasin n'a pas ferme sa porte : trois sacs partent pendant qu'on est dans les rayons.
        vendre(idCiment, "3");
        assertThat(stock(idCiment)).isEqualByComparingTo("7");

        // Puis on compte l'etagere, et l'on y trouve huit sacs.
        LigneComptageDto ligne = inventaireService.compter(seance.id(), idCiment, new BigDecimal("8"));
        // L'ecart affiche se mesure contre le theorique fige, qui ne bouge pas sous les yeux de
        // celui qui compte : 8 comptes contre 10 a l'ouverture.
        assertThat(ligne.ecart()).isEqualByComparingTo("-2");
        // La correction, elle, vise le stock au moment du comptage : il en manquait un.
        assertThat(ligne.correction()).isEqualByComparingTo("1");

        inventaireService.valider(seance.id());

        // Huit, comme sur l'etagere. Corriger vers le theorique de l'ouverture aurait donne cinq,
        // et la vente des trois sacs aurait ete comptee deux fois.
        assertThat(stock(idCiment)).isEqualByComparingTo("8");
    }

    @Test
    void abandonner_ferme_la_seance_sans_rien_corriger() {
        SeanceInventaireDto seance = inventaireService.ouvrir(null);
        inventaireService.compter(seance.id(), idCiment, new BigDecimal("2"));

        SeanceInventaireDto close = inventaireService.abandonner(seance.id());

        assertThat(close.statut()).isEqualTo(StatutSeanceInventaire.ABANDONNEE);
        assertThat(stock(idCiment)).isEqualByComparingTo("10");
    }

    @Test
    void une_seance_close_ne_se_compte_plus() {
        SeanceInventaireDto seance = inventaireService.ouvrir(null);
        inventaireService.valider(seance.id());

        assertThatThrownBy(() -> inventaireService.compter(seance.id(), idCiment, BigDecimal.ONE))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("close");
    }

    @Test
    void la_vue_des_ecarts_ne_rend_que_les_lignes_qui_ne_tombent_pas_juste() {
        SeanceInventaireDto seance = inventaireService.ouvrir(null);
        inventaireService.compter(seance.id(), idCiment, new BigDecimal("8"));
        inventaireService.compter(seance.id(), idFil, new BigDecimal("5"));

        // Celle qu'on regarde avant de valider : on ne relit pas mille lignes justes pour trouver
        // les douze qui ne le sont pas.
        assertThat(inventaireService.lignes(seance.id(), "", "ECARTS", PageRequest.of(0, 50))
                .getContent())
                .singleElement()
                .satisfies(l -> assertThat(l.idArticle()).isEqualTo(idCiment));

        assertThat(inventaireService.lignes(seance.id(), "", "A_COMPTER", PageRequest.of(0, 50))
                .getContent()).isEmpty();
    }

    @Test
    void la_seance_en_cours_se_retrouve_sans_la_chercher() {
        assertThat(inventaireService.seanceOuverte()).isNull();

        SeanceInventaireDto ouverte = inventaireService.ouvrir(null);
        assertThat(inventaireService.seanceOuverte().id()).isEqualTo(ouverte.id());

        inventaireService.abandonner(ouverte.id());
        // Ne pas avoir d'inventaire en cours est l'etat ordinaire d'un magasin.
        assertThat(inventaireService.seanceOuverte()).isNull();
    }
}
