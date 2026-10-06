package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeFourDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FactureDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCmndeFournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneReceptionDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ReglementDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ModeReglement;
import com.jumpy.tech.gestionstock.gestiondestock.entities.MotifMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.promotion.Calendrier;
import com.jumpy.tech.gestionstock.gestiondestock.rapport.RapportPertesDto;
import com.jumpy.tech.gestionstock.gestiondestock.rapport.RapportPertesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** R2 : ou le commerce perd — demarque, impayes, stock dormant. */
class RapportPertesTest extends AbstractIntegrationTest {

    @Autowired
    private EntrepriseService entrepriseService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private FournisseurService fournisseurService;
    @Autowired
    private CommandeFourService commandeFourService;
    @Autowired
    private MvtStkService mvtStkService;
    @Autowired
    private VenteService venteService;
    @Autowired
    private FactureService factureService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private RapportPertesService rapportPertesService;
    @Autowired
    private Calendrier calendrier;

    private Long idArticle;
    private LocalDate aujourdhui;

    @BeforeEach
    void unArticleAchete500() {
        Long idEntreprise = entrepriseService.save(EntrepriseDto.builder()
                .nom("Superette " + UUID.randomUUID())
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .build()).getId();
        UserDetailsImpl principal = new UserDetailsImpl(1L, "gerant", "g@exemple.test", "x", idEntreprise,
                List.of(new SimpleGrantedAuthority(ERole.ROLE_ADMIN.name())));
        principal.setIdsSites(Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        aujourdhui = calendrier.aujourdhui();

        CategoryDto rayon = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID()).designation("Épicerie").build());
        idArticle = articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID()).designation("Huile 1 L")
                .prixUnitaireHt(new BigDecimal("1000")).category(rayon).build()).getId();
        FournisseurDto fournisseur = fournisseurService.save(FournisseurDto.builder().nom("Grossiste")
                .mail("g" + UUID.randomUUID() + "@exemple.test").tel("690000011").build());
        CommandeFourDto achat = commandeFourService.save(CommandeFourDto.builder()
                .code("CF-" + UUID.randomUUID()).dateCommande(Instant.now()).fournisseur(fournisseur)
                .ligneCmndeFournisseur(List.of(LigneCmndeFournisseurDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal("20")).prixUnitaire(new BigDecimal("500")).build()))
                .build());
        commandeFourService.mettreAJourEtat(achat.getId(), EtatCommande.VALIDEE);
        LigneReceptionDto reception = new LigneReceptionDto();
        reception.setIdLigne(commandeFourService.lignes(achat.getId()).get(0).getId());
        reception.setQuantite(new BigDecimal("20"));
        commandeFourService.recevoir(achat.getId(), List.of(reception));
    }

    @AfterEach
    void oublier() {
        SecurityContextHolder.clearContext();
    }

    private void sortir(MotifMvtStk motif, String quantite) {
        mvtStkService.sortieStock(MvtStkDto.builder().article(ArticleDto.builder().Id(idArticle).build())
                .quantite(new BigDecimal(quantite)).motif(motif).build());
    }

    private VenteDto vendre(String quantite, ClientDto client) {
        return venteService.save(VenteDto.builder()
                .code("V-" + UUID.randomUUID())
                .client(client)
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal(quantite)).build()))
                .build());
    }

    private RapportPertesDto aujourdhui() {
        return rapportPertesService.pertes(aujourdhui, aujourdhui, false);
    }

    @Test
    void la_demarque_se_valorise_au_cout_et_l_inventaire_en_surplus_la_reduit() {
        sortir(MotifMvtStk.CASSE, "2");
        sortir(MotifMvtStk.PEREMPTION, "1");
        // Un rattrapage d'inventaire : 1 manquant puis 1 surplus ailleurs se compensent.
        mvtStkService.corrigerAuComptage(idArticle, new BigDecimal("-1"));
        mvtStkService.corrigerAuComptage(idArticle, new BigDecimal("1"));
        // Un retour au fournisseur n'est pas une perte : il est rembourse.
        sortir(MotifMvtStk.RETOUR_FOURNISSEUR, "3");
        vendre("10", null);

        RapportPertesDto.Demarque demarque = aujourdhui().getDemarque();
        assertThat(demarque.getValeur()).isEqualByComparingTo("1500");
        assertThat(demarque.getChiffreAffaires()).isEqualByComparingTo("10000");
        assertThat(demarque.getTauxDuChiffre()).isEqualByComparingTo("15.0");
        assertThat(demarque.isEstimee()).isFalse();
        assertThat(demarque.getParMotif()).extracting(RapportPertesDto.Poste::getCle)
                .containsExactly("CASSE", "PEREMPTION", "INVENTAIRE_MANQUANT", "INVENTAIRE_SURPLUS");
        assertThat(demarque.getParMotif().get(3).getValeur()).isEqualByComparingTo("-500");
        assertThat(demarque.getParArticle()).singleElement()
                .satisfies(a -> assertThat(a.getLibelle()).isEqualTo("Huile 1 L"));
    }

    @Test
    void les_impayes_se_rangent_par_client_et_par_anciennete() {
        ClientDto client = clientService.save(ClientDto.builder().nom("Ewondo").prenoms("Alice")
                .mail("alice" + UUID.randomUUID() + "@exemple.test").numTel("690000012").build());
        FactureDto due = factureService.emettre(vendre("3", client).getId());
        FactureDto partielle = factureService.emettre(vendre("2", client).getId());
        factureService.regler(partielle.getId(), ReglementDto.builder()
                .montant(new BigDecimal("500")).mode(ModeReglement.ESPECES).build());
        FactureDto soldee = factureService.emettre(vendre("1", null).getId());
        factureService.regler(soldee.getId(), ReglementDto.builder()
                .montant(soldee.getTotalTtc()).mode(ModeReglement.ESPECES).build());

        RapportPertesDto.Impayes impayes = aujourdhui().getImpayes();
        BigDecimal attendu = due.getTotalTtc().add(partielle.getTotalTtc()).subtract(new BigDecimal("500"));
        assertThat(impayes.getTotal()).isEqualByComparingTo(attendu);
        assertThat(impayes.getFactures()).isEqualTo(2);
        assertThat(impayes.getParAnciennete().get(0).getFactures()).isEqualTo(2);
        assertThat(impayes.getParClient()).singleElement().satisfies(c -> {
            assertThat(c.getNom()).isEqualTo("Alice Ewondo");
            assertThat(c.getTelephone()).isEqualTo("690000012");
            assertThat(c.getFactures()).isEqualTo(2);
            assertThat(c.getJoursDeRetard()).isZero();
        });
    }

    @Test
    void le_stock_qui_ne_se_vend_pas_dort() {
        RapportPertesDto.Dormants dormants = aujourdhui().getDormants();
        assertThat(dormants.getNombre()).isEqualTo(1);
        assertThat(dormants.getValeur()).isEqualByComparingTo("10000");
        assertThat(dormants.getArticles()).singleElement()
                .satisfies(a -> assertThat(a.getDesignation()).isEqualTo("Huile 1 L"));
    }
}
