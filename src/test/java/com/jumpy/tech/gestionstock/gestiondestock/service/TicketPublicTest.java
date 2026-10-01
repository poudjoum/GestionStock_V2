package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CampagneDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ReglementDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.TicketPublicDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ModeReglement;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.fidelite.CodeTicket;
import com.jumpy.tech.gestionstock.gestiondestock.fidelite.TicketsPublics;
import com.jumpy.tech.gestionstock.gestiondestock.promotion.Calendrier;
import com.jumpy.tech.gestionstock.gestiondestock.promotion.Campagnes;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EntrepriseRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le ticket retrouve par le code de son QR : ce que le client voit en le scannant.
 *
 * Le magasin ne collecte pas la TVA dans ces tests, pour que les totaux se lisent sans calcul :
 * un sac a 5 000 F coute 5 000 F.
 */
class TicketPublicTest extends AbstractIntegrationTest {

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
    private TicketsPublics tickets;
    @Autowired
    private Campagnes campagnes;
    @Autowired
    private Calendrier calendrier;

    private Long idEntreprise;
    private Long ciment;
    private Long fer;

    @BeforeEach
    void unMagasinEtDeuxArticles() {
        idEntreprise = entrepriseService.save(EntrepriseDto.builder()
                .nom("Quincaillerie du Port")
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .assujettieTva(false)
                .build()).getId();
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

    private void connecte(Long entreprise) {
        UserDetailsImpl principal = new UserDetailsImpl(1L, "gerant", "gerant@exemple.test", "x",
                entreprise, List.of(new SimpleGrantedAuthority(ERole.ROLE_ADMIN.name())));
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

    private static LigneVenteDto ligne(Long article, String quantite, String prix) {
        return LigneVenteDto.builder()
                .article(ArticleDto.builder().Id(article).build())
                .quantite(new BigDecimal(quantite))
                .prixUnitaire(new BigDecimal(prix))
                .build();
    }

    /** Deux sacs et deux barres : 17 000 F. */
    private VenteDto venteEncaissee(String codeTicket) {
        return venteService.synchroniser(VenteDto.builder()
                .code("V-" + UUID.randomUUID())
                .referenceClient(UUID.randomUUID().toString())
                .codeTicket(codeTicket)
                .datevente(Instant.now().minus(Duration.ofMinutes(5)))
                .ligneVente(List.of(ligne(ciment, "2", "5000"), ligne(fer, "2", "3500")))
                .encaissement(ReglementDto.builder()
                        .montant(new BigDecimal("17000")).mode(ModeReglement.ESPECES).build())
                .build());
    }

    // --- Le code ------------------------------------------------------------------------------

    @Test
    void une_vente_sans_code_en_recoit_un() {
        VenteDto vente = venteService.save(VenteDto.builder()
                .code("V-" + UUID.randomUUID())
                .ligneVente(List.of(ligne(ciment, "1", "5000")))
                .build());

        // Une vente saisie ailleurs qu'au comptoir a un ticket comme les autres.
        assertThat(CodeTicket.lire(vente.getCodeTicket())).contains(vente.getCodeTicket());
    }

    @Test
    void le_code_tire_par_le_comptoir_est_garde() {
        // Il est deja imprime sur le papier du client, hors ligne peut-etre.
        assertThat(venteEncaissee("7K3M9P2QA4TZ").getCodeTicket()).isEqualTo("7K3M9P2QA4TZ");
    }

    @Test
    void un_code_mal_forme_est_refuse_plutot_que_remplace() {
        // Remplace, il ne serait plus celui du papier : le QR menerait nulle part.
        assertThatThrownBy(() -> venteEncaissee("PAS-UN-CODE"))
                .isInstanceOf(InvalidEntityException.class);
    }

    // --- Le ticket du client ------------------------------------------------------------------

    @Test
    void le_qr_retrouve_les_articles_le_total_et_les_points() {
        uneCampagneEnCours();
        String code = venteEncaissee(null).getCodeTicket();
        SecurityContextHolder.clearContext();   // le client n'a pas de compte

        TicketPublicDto ticket = tickets.parCode(code);

        assertThat(ticket.getMagasin()).isEqualTo("Quincaillerie du Port");
        assertThat(ticket.getArticles())
                .extracting(TicketPublicDto.Ligne::getDesignation)
                .containsExactlyInAnyOrder("Sac de ciment 50 kg", "Barre de fer 12 mm");
        assertThat(ticket.getArticles())
                .filteredOn(l -> l.getDesignation().startsWith("Sac"))
                .singleElement()
                .satisfies(l -> {
                    assertThat(l.getQuantite()).isEqualByComparingTo("2");
                    assertThat(l.getMontant()).isEqualByComparingTo("10000");
                });
        assertThat(ticket.getTotalTtc()).isEqualByComparingTo("17000");
        // 17 000 F a 10 000 F le point : un point, les 7 000 restants ne comptent pas.
        assertThat(ticket.getPoints()).isEqualTo(1);
        assertThat(ticket.isFideliteActive()).isTrue();
    }

    @Test
    void un_code_recopie_a_la_main_retrouve_le_ticket() {
        String code = venteEncaissee(null).getCodeTicket();

        String recopie = code.substring(0, 4).toLowerCase() + "-" + code.substring(4, 8) + "-" + code.substring(8);
        assertThat(tickets.parCode(recopie).getCode()).isEqualTo(code);
    }

    @Test
    void le_montant_pour_un_point_est_celui_du_commerce() {
        uneCampagneEnCours();
        var entreprise = entrepriseRepository.findById(idEntreprise).orElseThrow();
        entreprise.setMontantParPoint(new BigDecimal("5000"));
        entrepriseRepository.save(entreprise);

        String code = venteEncaissee(null).getCodeTicket();

        assertThat(tickets.parCode(code).getPoints()).isEqualTo(3);
    }

    @Test
    void hors_campagne_le_ticket_ne_rapporte_pas_de_points() {
        String code = venteEncaissee(null).getCodeTicket();

        TicketPublicDto ticket = tickets.parCode(code);
        assertThat(ticket.getPoints()).isZero();
        assertThat(ticket.getArticles()).hasSize(2);
    }

    /** Les points sont ceux des campagnes : sans campagne, un ticket ne rapporte rien. */
    private void uneCampagneEnCours() {
        campagnes.creer(CampagneDto.builder()
                .titre("Rentrée")
                .dateDebut(calendrier.aujourdhui())
                .dateFin(calendrier.aujourdhui().plusDays(7))
                .build());
    }

    @Test
    void sans_programme_le_ticket_ne_vaut_rien() {
        var entreprise = entrepriseRepository.findById(idEntreprise).orElseThrow();
        entreprise.setFideliteActive(false);
        entrepriseRepository.save(entreprise);

        String code = venteEncaissee(null).getCodeTicket();

        TicketPublicDto ticket = tickets.parCode(code);
        assertThat(ticket.isFideliteActive()).isFalse();
        assertThat(ticket.getPoints()).isZero();
        // Les articles restent consultables : c'est l'achat du client, programme ou non.
        assertThat(ticket.getArticles()).hasSize(2);
    }

    @Test
    void une_vente_annulee_ne_rapporte_rien_et_le_dit() {
        VenteDto vente = venteService.synchroniser(VenteDto.builder()
                .code("V-" + UUID.randomUUID())
                .referenceClient(UUID.randomUUID().toString())
                .datevente(Instant.now().minus(Duration.ofMinutes(5)))
                .ligneVente(List.of(ligne(ciment, "4", "5000")))
                .build());
        venteService.annuler(vente.getId());

        TicketPublicDto ticket = tickets.parCode(vente.getCodeTicket());

        assertThat(ticket.isAnnulee()).isTrue();
        assertThat(ticket.getPoints()).isZero();
    }

    @Test
    void un_code_inconnu_ou_mal_forme_ne_trouve_rien_et_ne_dit_pas_lequel() {
        // Les deux cas rendent la meme reponse : les distinguer apprendrait a qui essaie des
        // codes lesquels ont la bonne forme.
        assertThatThrownBy(() -> tickets.parCode("ZZZZZZZZZZZZ")).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> tickets.parCode("n'importe quoi")).isInstanceOf(EntityNotFoundException.class);
    }

    // --- Le reglage du commerce ---------------------------------------------------------------

    @Test
    void le_commerce_regle_son_programme() {
        EntrepriseDto mienne = entrepriseService.mienne();
        mienne.setMontantParPoint(new BigDecimal("2000"));
        mienne.setFideliteActive(true);

        EntrepriseDto enregistree = entrepriseService.mettreAJourMienne(mienne);

        assertThat(enregistree.getMontantParPoint()).isEqualByComparingTo("2000");
    }

    @Test
    void un_ecran_qui_ignore_le_programme_ne_le_remet_pas_a_zero() {
        var entreprise = entrepriseRepository.findById(idEntreprise).orElseThrow();
        entreprise.setMontantParPoint(new BigDecimal("2000"));
        entrepriseRepository.save(entreprise);

        EntrepriseDto mienne = entrepriseService.mienne();
        mienne.setFideliteActive(null);
        mienne.setMontantParPoint(null);
        entrepriseService.mettreAJourMienne(mienne);

        assertThat(entrepriseRepository.findById(idEntreprise).orElseThrow().getMontantParPoint())
                .isEqualByComparingTo("2000");
    }

    @Test
    void un_montant_pour_un_point_nul_ou_negatif_est_refuse() {
        EntrepriseDto mienne = entrepriseService.mienne();
        mienne.setMontantParPoint(BigDecimal.ZERO);

        assertThatThrownBy(() -> entrepriseService.mettreAJourMienne(mienne))
                .isInstanceOf(InvalidEntityException.class);
    }
}
