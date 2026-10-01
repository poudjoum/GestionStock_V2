package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CampagneDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CampagnePubliqueDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.PromotionArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Campagne;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeRemise;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.promotion.Calendrier;
import com.jumpy.tech.gestionstock.gestiondestock.promotion.Campagnes;
import com.jumpy.tech.gestionstock.gestiondestock.promotion.CampagnesPubliques;
import com.jumpy.tech.gestionstock.gestiondestock.repository.CampagneRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EntrepriseRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneVenteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Les campagnes de promotion : preparees par le gerant, appliquees en caisse, montrees dans
 * l'application.
 *
 * Le magasin ne collecte pas la TVA, sauf mention contraire : un sac a 5 000 F HT coute 5 000 F.
 */
class CampagnesTest extends AbstractIntegrationTest {

    @Autowired
    private Campagnes campagnes;
    @Autowired
    private CampagnesPubliques vitrine;
    @Autowired
    private CampagneRepository campagneRepository;
    @Autowired
    private VenteService venteService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private MvtStkService mvtStkService;
    @Autowired
    private EntrepriseService entrepriseService;
    @Autowired
    private EntrepriseRepository entrepriseRepository;
    @Autowired
    private LigneVenteRepository ligneVenteRepository;
    @Autowired
    private Calendrier calendrier;

    private Long idEntreprise;
    private Long ciment;
    private Long fer;
    private LocalDate aujourdhui;

    @BeforeEach
    void unMagasinEtDeuxArticles() {
        aujourdhui = calendrier.aujourdhui();
        idEntreprise = magasin("Quincaillerie du Port");
        connecte(idEntreprise);
        CategoryDto categorie = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID()).designation("Divers").build());
        ciment = article("Sac de ciment 50 kg", "5000", categorie);
        fer = article("Barre de fer 12 mm", "3500", categorie);
    }

    @AfterEach
    void oublierLUtilisateur() {
        SecurityContextHolder.clearContext();
    }

    // --- En caisse ----------------------------------------------------------------------------

    @Test
    void au_comptoir_l_article_se_vend_au_prix_de_la_campagne() {
        campagne("Rentrée", aujourdhui, aujourdhui.plusDays(7),
                promo(ciment, TypeRemise.POURCENTAGE, "20"),
                promo(fer, TypeRemise.PRIX_FIXE, "3000"));

        // La caisse envoie le prix normal : sa copie des promotions date d'avant la campagne.
        VenteDto vente = venteService.save(vente(ligne(ciment, "5000"), ligne(fer, "3500")));

        assertThat(prixVendus(vente)).containsExactlyInAnyOrder(
                new BigDecimal("4000.00"), new BigDecimal("3000.00"));
    }

    @Test
    void une_remise_deja_accordee_par_la_caisse_n_est_pas_reprise() {
        campagne("Rentrée", aujourdhui, aujourdhui.plusDays(7), promo(ciment, TypeRemise.POURCENTAGE, "10"));

        VenteDto vente = venteService.save(vente(ligne(ciment, "4000")));

        assertThat(prixVendus(vente)).containsExactly(new BigDecimal("4000.00"));
    }

    @Test
    void hors_campagne_le_prix_est_le_prix_normal() {
        campagne("Plus tard", aujourdhui.plusDays(3), aujourdhui.plusDays(10), promo(ciment, TypeRemise.POURCENTAGE, "50"));

        VenteDto vente = venteService.save(vente(ligne(ciment, "5000")));

        assertThat(prixVendus(vente)).containsExactly(new BigDecimal("5000.00"));
    }

    @Test
    void une_vente_faite_hors_ligne_garde_le_prix_que_le_client_a_paye() {
        campagne("Rentrée", aujourdhui, aujourdhui.plusDays(7), promo(ciment, TypeRemise.POURCENTAGE, "20"));

        // La caisse sans reseau ne connaissait pas la campagne : le client a paye 5 000 F, et son
        // ticket le dit. Le serveur ne reecrit pas ce qui a ete encaisse.
        VenteDto vente = venteService.synchroniser(VenteDto.builder()
                .code("V-" + UUID.randomUUID())
                .referenceClient(UUID.randomUUID().toString())
                .datevente(Instant.now().minus(Duration.ofMinutes(5)))
                .ligneVente(List.of(ligne(ciment, "5000")))
                .build());

        assertThat(prixVendus(vente)).containsExactly(new BigDecimal("5000.00"));
    }

    @Test
    void une_campagne_arretee_rend_les_prix_normaux() {
        CampagneDto rentree = campagne("Rentrée", aujourdhui, aujourdhui.plusDays(7),
                promo(ciment, TypeRemise.POURCENTAGE, "20"));

        CampagneDto arretee = campagnes.arreter(rentree.getId());
        VenteDto vente = venteService.save(vente(ligne(ciment, "5000")));

        assertThat(arretee.getStatut()).isEqualTo(CampagneDto.Statut.ARRETEE);
        assertThat(prixVendus(vente)).containsExactly(new BigDecimal("5000.00"));
        assertThat(campagnes.promotionsEnCours()).isEmpty();
    }

    @Test
    void la_caisse_recoit_les_promotions_du_jour_avec_leur_prix() {
        campagne("Rentrée", aujourdhui, aujourdhui.plusDays(7), promo(ciment, TypeRemise.POURCENTAGE, "20"));
        campagne("Plus tard", aujourdhui.plusDays(8), aujourdhui.plusDays(10), promo(fer, TypeRemise.POURCENTAGE, "20"));

        assertThat(campagnes.promotionsEnCours())
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.getIdArticle()).isEqualTo(ciment);
                    assertThat(p.getPrixPromoHt()).isEqualByComparingTo("4000");
                    assertThat(p.getDateFin()).isEqualTo(aujourdhui.plusDays(7));
                });
    }

    // --- Ce que le gerant peut saisir ---------------------------------------------------------

    @Test
    void un_prix_fixe_au_dessus_du_prix_normal_est_refuse() {
        assertThatThrownBy(() -> campagne("Rentrée", aujourdhui, aujourdhui.plusDays(7),
                promo(ciment, TypeRemise.PRIX_FIXE, "5000")))
                .isInstanceOf(InvalidEntityException.class)
                .satisfies(e -> assertThat(((InvalidEntityException) e).getErrors())
                        .anyMatch(m -> m.contains("inférieur au prix normal")));
    }

    @Test
    void une_reduction_de_cent_pour_cent_est_refusee() {
        assertThatThrownBy(() -> campagne("Rentrée", aujourdhui, aujourdhui.plusDays(7),
                promo(ciment, TypeRemise.POURCENTAGE, "100")))
                .isInstanceOf(InvalidEntityException.class);
    }

    @Test
    void un_article_n_a_qu_une_promotion_a_la_fois() {
        campagne("Rentrée", aujourdhui, aujourdhui.plusDays(7), promo(ciment, TypeRemise.POURCENTAGE, "20"));

        assertThatThrownBy(() -> campagne("Fin de mois", aujourdhui.plusDays(5), aujourdhui.plusDays(12),
                promo(ciment, TypeRemise.POURCENTAGE, "10")))
                .isInstanceOf(InvalidEntityException.class)
                .satisfies(e -> assertThat(((InvalidEntityException) e).getErrors())
                        .anyMatch(m -> m.contains("déjà en promotion")));
    }

    @Test
    void une_campagne_ne_se_termine_pas_dans_le_passe() {
        assertThatThrownBy(() -> campagne("Hier", aujourdhui.minusDays(5), aujourdhui.minusDays(1)))
                .isInstanceOf(InvalidEntityException.class);
    }

    @Test
    void une_campagne_terminee_ne_se_modifie_plus() {
        Campagne finie = new Campagne();
        finie.setIdEntreprise(idEntreprise);
        finie.setTitre("Soldes d'hiver");
        finie.setDateDebut(aujourdhui.minusDays(30));
        finie.setDateFin(aujourdhui.minusDays(20));
        Long id = campagneRepository.save(finie).getId();

        assertThatThrownBy(() -> campagnes.modifier(id, CampagneDto.builder()
                .titre("Soldes d'hiver").dateDebut(aujourdhui).dateFin(aujourdhui.plusDays(3)).build()))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("ne se modifie plus");
    }

    @Test
    void le_voisin_ne_met_pas_mes_articles_en_promotion_ni_ne_voit_mes_campagnes() {
        CampagneDto mienne = campagne("Rentrée", aujourdhui, aujourdhui.plusDays(7));

        connecte(magasin("Le voisin"));

        assertThatThrownBy(() -> campagnes.lire(mienne.getId())).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> campagne("Pirate", aujourdhui, aujourdhui.plusDays(7),
                promo(ciment, TypeRemise.POURCENTAGE, "90")))
                .isInstanceOf(InvalidEntityException.class);
        assertThat(campagnes.lister()).isEmpty();
    }

    // --- La vitrine de l'application ----------------------------------------------------------

    @Test
    void la_vitrine_montre_les_prix_toutes_taxes_comprises() {
        var magasin = entrepriseRepository.findById(idEntreprise).orElseThrow();
        magasin.setAssujettieTva(true);
        magasin.setTauxTva(new BigDecimal("19.25"));
        entrepriseRepository.save(magasin);
        CampagneDto rentree = campagne("Rentrée", aujourdhui, aujourdhui.plusDays(7),
                promo(ciment, TypeRemise.POURCENTAGE, "20"));
        SecurityContextHolder.clearContext();   // le client n'a pas de compte

        CampagnePubliqueDto vue = vitrine.enCours().stream()
                .filter(c -> c.getId().equals(rentree.getId())).findFirst().orElseThrow();

        assertThat(vue.getNomMagasin()).isEqualTo("Quincaillerie du Port");
        assertThat(vue.getArticles()).singleElement().satisfies(a -> {
            assertThat(a.getPrixNormalTtc()).isEqualByComparingTo("5963");   // 5 000 x 1,1925
            assertThat(a.getPrixPromoTtc()).isEqualByComparingTo("4770");    // 4 000 x 1,1925
            assertThat(a.getRemisePourcent()).isEqualTo(20);
        });
    }

    @Test
    void un_magasin_suspendu_disparait_de_la_vitrine() {
        CampagneDto rentree = campagne("Rentrée", aujourdhui, aujourdhui.plusDays(7));
        var magasin = entrepriseRepository.findById(idEntreprise).orElseThrow();
        magasin.setSuspendue(true);
        entrepriseRepository.save(magasin);

        assertThat(vitrine.enCours()).noneMatch(c -> c.getId().equals(rentree.getId()));
    }

    // --- Outils -------------------------------------------------------------------------------

    private CampagneDto campagne(String titre, LocalDate debut, LocalDate fin, PromotionArticleDto... promotions) {
        return campagnes.creer(CampagneDto.builder()
                .titre(titre)
                .message("Profitez-en !")
                .dateDebut(debut)
                .dateFin(fin)
                .promotions(List.of(promotions))
                .build());
    }

    private static PromotionArticleDto promo(Long article, TypeRemise type, String valeur) {
        return PromotionArticleDto.builder().idArticle(article).typeRemise(type).valeur(new BigDecimal(valeur)).build();
    }

    private List<BigDecimal> prixVendus(VenteDto vente) {
        return ligneVenteRepository.findAllByVenteId(vente.getId()).stream()
                .map(l -> l.getPrixUnitaire().setScale(2))
                .toList();
    }

    private static VenteDto vente(LigneVenteDto... lignes) {
        return VenteDto.builder().code("V-" + UUID.randomUUID()).ligneVente(List.of(lignes)).build();
    }

    private static LigneVenteDto ligne(Long article, String prix) {
        return LigneVenteDto.builder()
                .article(ArticleDto.builder().Id(article).build())
                .quantite(BigDecimal.ONE)
                .prixUnitaire(new BigDecimal(prix))
                .build();
    }

    private Long magasin(String nom) {
        return entrepriseService.save(EntrepriseDto.builder()
                .nom(nom)
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .assujettieTva(false)
                .build()).getId();
    }

    private void connecte(Long entreprise) {
        UserDetailsImpl principal = new UserDetailsImpl(1L, "gerant", "gerant@exemple.test", "x",
                entreprise, List.of(new SimpleGrantedAuthority(ERole.ROLE_MANAGER.name())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Long article(String designation, String prix, CategoryDto categorie) {
        Long id = articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID())
                .designation(designation)
                .prixUnitaireHt(new BigDecimal(prix))
                .category(categorie)
                .build()).getId();
        mvtStkService.entreeStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(id).build())
                .quantite(new BigDecimal("100")).build());
        return id;
    }
}
