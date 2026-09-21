package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.StatutStock;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La vente faite sans reseau, envoyee a la reconnexion.
 *
 * Elle est un fait a constater, non une transaction a autoriser : la marchandise est deja partie.
 * Trois consequences — une identite venue du poste de vente, sa date reelle, et un stock qui ne
 * s'y oppose pas.
 */
class VenteSynchroniseeTest extends AbstractIntegrationTest {

    @Autowired
    private VenteService venteService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private MvtStkService mvtStkService;
    @Autowired
    private StockService stockService;

    private Long idArticle;

    @BeforeEach
    void unArticle() {
        CategoryDto category = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID()).designation("Divers").build());
        idArticle = articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID())
                .designation("Sac de ciment")
                .prixUnitaireHt(new BigDecimal("5000"))
                .category(category)
                .build()).getId();
    }

    /**
     * Un instant passe, tronque a la milliseconde.
     *
     * `Instant` compte en nanosecondes, PostgreSQL en microsecondes : une date non tronquee
     * revient de la base arrondie, et la comparaison echoue sur un chiffre que personne ne voit.
     */
    private static Instant ilYA(Duration duree) {
        return Instant.now().minus(duree).truncatedTo(ChronoUnit.MILLIS);
    }

    private void approvisionner(String quantite) {
        mvtStkService.entreeStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(idArticle).build())
                .quantite(new BigDecimal(quantite)).build());
    }

    private VenteDto venteHorsLigne(String reference, Instant quand, String quantite) {
        return VenteDto.builder()
                .code("V-" + UUID.randomUUID())
                .referenceClient(reference)
                .datevente(quand)
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal(quantite))
                        .prixUnitaire(new BigDecimal("5000"))
                        .build()))
                .build();
    }

    // --- Idempotence --------------------------------------------------------------------------

    @Test
    void rejouer_le_meme_envoi_ne_vend_pas_deux_fois() {
        approvisionner("100");
        String reference = UUID.randomUUID().toString();
        VenteDto envoi = venteHorsLigne(reference, Instant.now().minus(Duration.ofHours(3)), "4");

        VenteDto premiere = venteService.synchroniser(envoi);
        VenteDto seconde = venteService.synchroniser(envoi);

        // Un poste qui perd le reseau au milieu d'un envoi reessaie sans savoir si le premier est
        // passe : il doit retrouver son travail fait, pas le refaire.
        assertThat(seconde.getId()).isEqualTo(premiere.getId());
        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("96");
    }

    @Test
    void deux_references_differentes_font_deux_ventes() {
        approvisionner("100");
        Instant quand = Instant.now().minus(Duration.ofHours(1));

        VenteDto une = venteService.synchroniser(
                venteHorsLigne(UUID.randomUUID().toString(), quand, "4"));
        VenteDto deux = venteService.synchroniser(
                venteHorsLigne(UUID.randomUUID().toString(), quand, "4"));

        assertThat(deux.getId()).isNotEqualTo(une.getId());
        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("92");
    }

    @Test
    void une_vente_synchronisee_sans_reference_est_refusee() {
        approvisionner("100");
        VenteDto sansReference = venteHorsLigne(null, Instant.now().minus(Duration.ofHours(1)), "4");

        // Sans elle, un envoi rejoue enregistrerait une seconde vente.
        assertThatThrownBy(() -> venteService.synchroniser(sansReference))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("référence");

        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("100");
    }

    @Test
    void la_reference_remonte_avec_la_vente() {
        approvisionner("100");
        String reference = UUID.randomUUID().toString();

        VenteDto vente = venteService.synchroniser(
                venteHorsLigne(reference, Instant.now().minus(Duration.ofHours(1)), "1"));

        // Le poste de vente doit pouvoir rapprocher ce que le serveur lui rend de ce qu'il a en
        // file : sans la reference rendue, il ne saurait pas quelle vente marquer comme envoyee.
        assertThat(vente.getReferenceClient()).isEqualTo(reference);
        assertThat(venteService.findById(vente.getId()).getReferenceClient()).isEqualTo(reference);
    }

    @Test
    void une_vente_directe_reste_possible_sans_reference() {
        approvisionner("100");

        VenteDto vente = venteService.save(VenteDto.builder()
                .code("V-" + UUID.randomUUID())
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal("2"))
                        .prixUnitaire(new BigDecimal("5000"))
                        .build()))
                .build());

        // La vente de comptoir n'a rien a rejouer : la reference lui reste facultative.
        assertThat(vente.getReferenceClient()).isNull();
        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("98");
    }

    @Test
    void une_vente_directe_rejouee_avec_sa_reference_ne_vend_pas_deux_fois() {
        approvisionner("100");
        String reference = UUID.randomUUID().toString();
        VenteDto envoi = VenteDto.builder()
                .code("V-" + UUID.randomUUID())
                .referenceClient(reference)
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal("2"))
                        .prixUnitaire(new BigDecimal("5000"))
                        .build()))
                .build();

        // Le comptoir aussi peut perdre sa reponse : la meme protection lui est ouverte.
        VenteDto premiere = venteService.save(envoi);
        VenteDto seconde = venteService.save(envoi);

        assertThat(seconde.getId()).isEqualTo(premiere.getId());
        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("98");
    }

    // --- La date ------------------------------------------------------------------------------

    @Test
    void la_vente_garde_la_date_a_laquelle_elle_a_eu_lieu() {
        approvisionner("100");
        Instant ceMatin = ilYA(Duration.ofHours(5));

        VenteDto vente = venteService.synchroniser(
                venteHorsLigne(UUID.randomUUID().toString(), ceMatin, "3"));

        // Une vente de 9 h synchronisee a midi doit peser sur la caisse de 9 h.
        assertThat(vente.getDatevente()).isEqualTo(ceMatin);
    }

    @Test
    void le_mouvement_de_stock_porte_la_meme_date_que_la_vente() {
        approvisionner("100");
        Instant ceMatin = ilYA(Duration.ofHours(5));

        venteService.synchroniser(venteHorsLigne(UUID.randomUUID().toString(), ceMatin, "3"));

        // Sinon l'historique de l'article montrerait une sortie a midi pour une vente de 9 h.
        //
        // La sortie se cherche par son sens, et non en prenant le mouvement le plus recent :
        // justement parce qu'elle est antidatee, l'approvisionnement du test lui est posterieur.
        MvtStkDto sortie = mvtStkService.mvtStkArticle(idArticle).stream()
                .filter(m -> m.getTypeMvt() == TypeMvtStk.SORTIE)
                .findFirst()
                .orElseThrow();
        assertThat(sortie.getDateMvt()).isEqualTo(ceMatin);
    }

    @Test
    void une_vente_synchronisee_sans_date_est_refusee() {
        approvisionner("100");
        VenteDto sansDate = venteHorsLigne(UUID.randomUUID().toString(), null, "4");

        assertThatThrownBy(() -> venteService.synchroniser(sansDate))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("date");
    }

    @Test
    void une_vente_datee_dans_le_futur_est_refusee() {
        approvisionner("100");
        VenteDto demain = venteHorsLigne(UUID.randomUUID().toString(),
                Instant.now().plus(Duration.ofDays(1)), "4");

        assertThatThrownBy(() -> venteService.synchroniser(demain))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("futur");

        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("100");
    }

    @Test
    void quelques_minutes_d_avance_restent_tolerees() {
        approvisionner("100");
        // L'horloge d'un telephone derive : refuser une vente pour deux minutes d'avance rendrait
        // la synchronisation capricieuse.
        VenteDto unPeuEnAvance = venteHorsLigne(UUID.randomUUID().toString(),
                Instant.now().plus(Duration.ofMinutes(2)), "1");

        assertThat(venteService.synchroniser(unPeuEnAvance).getId()).isNotNull();
    }

    @Test
    void une_vente_directe_est_datee_par_le_serveur() {
        approvisionner("100");
        Instant avant = Instant.now().minusSeconds(1);

        VenteDto vente = venteService.save(VenteDto.builder()
                .code("V-" + UUID.randomUUID())
                // Antidater une vente de comptoir ferait entrer une recette dans une caisse deja
                // arretee : la date envoyee ne fait foi qu'a la synchronisation.
                .datevente(Instant.parse("2020-01-01T00:00:00Z"))
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal("1"))
                        .prixUnitaire(new BigDecimal("5000"))
                        .build()))
                .build());

        assertThat(vente.getDatevente()).isAfter(avant);
    }

    // --- Le stock -----------------------------------------------------------------------------

    @Test
    void une_vente_hors_ligne_passe_meme_si_le_stock_ne_suffit_plus() {
        approvisionner("3");

        // Deux caisses ont vendu hors ligne le dernier sac : la seconde arrive apres coup.
        VenteDto vente = venteService.synchroniser(
                venteHorsLigne(UUID.randomUUID().toString(), Instant.now().minus(Duration.ofHours(2)), "5"));

        // Refuser n'empecherait rien : cela effacerait seulement la trace de ce qui a eu lieu.
        assertThat(vente.getId()).isNotNull();
        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("-2");
    }

    @Test
    void un_stock_negatif_se_distingue_d_une_rupture() {
        approvisionner("3");
        venteService.synchroniser(
                venteHorsLigne(UUID.randomUUID().toString(), Instant.now().minus(Duration.ofHours(2)), "5"));

        // Les deux n'appellent pas le meme geste : la rupture se commande au fournisseur, le
        // negatif se compte sur l'etagere.
        var ligne = stockService.alertes().stream()
                .filter(l -> l.getIdArticle().equals(idArticle))
                .findFirst();
        assertThat(ligne).isPresent();
        assertThat(ligne.get().getStatut()).isEqualTo(StatutStock.NEGATIF);
        assertThat(ligne.get().getQuantite()).isEqualByComparingTo("-2");
    }

    @Test
    void un_stock_a_zero_reste_une_rupture() {
        approvisionner("3");
        venteService.synchroniser(
                venteHorsLigne(UUID.randomUUID().toString(), Instant.now().minus(Duration.ofHours(2)), "3"));

        var ligne = stockService.alertes().stream()
                .filter(l -> l.getIdArticle().equals(idArticle))
                .findFirst();
        assertThat(ligne).isPresent();
        assertThat(ligne.get().getStatut()).isEqualTo(StatutStock.RUPTURE);
    }

    @Test
    void une_vente_directe_reste_refusee_faute_de_stock() {
        approvisionner("3");

        // Au comptoir, le client est devant le caissier : lui vendre ce qui n'existe pas serait
        // lui promettre une marchandise absente.
        assertThatThrownBy(() -> venteService.save(VenteDto.builder()
                .code("V-" + UUID.randomUUID())
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal("5"))
                        .prixUnitaire(new BigDecimal("5000"))
                        .build()))
                .build()))
                .isInstanceOf(InvalidEntityException.class);

        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("3");
    }

    @Test
    void la_sortie_manuelle_reste_refusee_faute_de_stock() {
        approvisionner("3");

        // La route publique des mouvements n'emprunte pas le chemin des ventes synchronisees :
        // laisser un appelant quelconque sortir au-dela du stock permettrait d'en fabriquer un
        // qui n'a jamais existe.
        assertThatThrownBy(() -> mvtStkService.sortieStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(idArticle).build())
                .quantite(new BigDecimal("5")).build()))
                .isInstanceOf(InvalidEntityException.class);
    }
}
