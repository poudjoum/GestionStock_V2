package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.analyse.AnalyseArticlesDto;
import com.jumpy.tech.gestionstock.gestiondestock.analyse.AnalyseService;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeFourDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ConditionnementDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCmndeFournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCommandeClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneInventaireDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneReceptionDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;
import com.jumpy.tech.gestionstock.gestiondestock.entities.MotifMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.entities.StatutStock;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.reappro.CommandesReapproDto;
import com.jumpy.tech.gestionstock.gestiondestock.reappro.ReapproDto;
import com.jumpy.tech.gestionstock.gestiondestock.reappro.ReapproService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le lot D : ce qu'une commande client retient, ce qu'il faut recommander, ce que rapporte chaque
 * article.
 */
class ReservationEtReapproTest extends AbstractIntegrationTest {

    @Autowired
    private EntrepriseService entrepriseService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private ConditionnementService conditionnementService;
    @Autowired
    private MvtStkService mvtStkService;
    @Autowired
    private VenteService venteService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private CommandeClientService commandeClientService;
    @Autowired
    private CommandeFourService commandeFourService;
    @Autowired
    private FournisseurService fournisseurService;
    @Autowired
    private StockService stockService;
    @Autowired
    private ReapproService reapproService;
    @Autowired
    private AnalyseService analyseService;

    private Long idEntreprise;
    private CategoryDto category;
    private Long idArticle;
    private ClientDto client;

    @BeforeEach
    void unMagasin() {
        idEntreprise = entrepriseService.save(EntrepriseDto.builder()
                .nom("Quincaillerie " + UUID.randomUUID())
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .build()).getId();
        UserDetailsImpl principal = new UserDetailsImpl(1L, "gerant", "g@exemple.test", "x", idEntreprise,
                List.of(new SimpleGrantedAuthority(ERole.ROLE_ADMIN.name())));
        principal.setIdsSites(Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        category = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID()).designation("Ciment").build());
        idArticle = article("Ciment 50 kg", "5000");
        entrer(idArticle, "10");
        client = clientService.save(ClientDto.builder().nom("Mbarga").prenoms("Paul")
                .mail("paul" + UUID.randomUUID() + "@exemple.test").numTel("690000009").build());
    }

    @AfterEach
    void oublier() {
        SecurityContextHolder.clearContext();
    }

    private Long article(String designation, String prix) {
        return articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID()).designation(designation)
                .prixUnitaireHt(new BigDecimal(prix)).category(category).build()).getId();
    }

    private void entrer(Long id, String quantite) {
        mvtStkService.entreeStock(MvtStkDto.builder().article(ArticleDto.builder().Id(id).build())
                .quantite(new BigDecimal(quantite)).build());
    }

    private VenteDto vendre(Long id, String quantite) {
        return venteService.save(VenteDto.builder()
                .code("V-" + UUID.randomUUID())
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(id).build())
                        .quantite(new BigDecimal(quantite)).build()))
                .build());
    }

    private CommandeClientDto commander(String quantite) {
        CommandeClientDto commande = commandeClientService.save(CommandeClientDto.builder()
                .code("CC-" + UUID.randomUUID()).client(client)
                .ligneCmndeClients(List.of(LigneCommandeClientDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal(quantite)).build()))
                .build());
        return commandeClientService.mettreAJourEtat(commande.getId(), EtatCommande.VALIDEE);
    }

    private LigneInventaireDto ligne() {
        return stockService.inventaire(null, PageRequest.of(0, 10), false).getContent().stream()
                .filter(l -> l.getIdArticle().equals(idArticle)).findFirst().orElseThrow();
    }

    @Test
    void une_commande_validee_retient_sa_marchandise_et_le_comptoir_vend_le_reste() {
        CommandeClientDto commande = commander("6");

        // Elle se retrouve par le nom du client, parmi celles a servir.
        assertThat(commandeClientService.rechercher(List.of(EtatCommande.VALIDEE, EtatCommande.PARTIELLEMENT_LIVREE),
                "mbarg", PageRequest.of(0, 10)).getContent())
                .extracting(CommandeClientDto::getId).containsExactly(commande.getId());
        assertThat(commandeClientService.rechercher(List.of(EtatCommande.EN_PREPARATION), "", PageRequest.of(0, 10))
                .getContent()).isEmpty();

        assertThat(ligne().getReserve()).isEqualByComparingTo("6");
        assertThat(ligne().getDisponible()).isEqualByComparingTo("4");

        assertThatThrownBy(() -> vendre(idArticle, "5"))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("4 disponibles");
        vendre(idArticle, "4");

        // La commande puise dans ce qu'elle avait reserve.
        venteService.servirCommandeClient(commande.getId());
        assertThat(ligne().getQuantite()).isEqualByComparingTo("0");
        assertThat(ligne().getReserve()).isEqualByComparingTo("0");
    }

    @Test
    void une_commande_en_preparation_ne_retient_rien_et_l_annulation_libere() {
        CommandeClientDto brouillon = commandeClientService.save(CommandeClientDto.builder()
                .code("CC-" + UUID.randomUUID()).client(client)
                .ligneCmndeClients(List.of(LigneCommandeClientDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal("8")).build()))
                .build());
        assertThat(ligne().getReserve()).isEqualByComparingTo("0");

        commandeClientService.mettreAJourEtat(brouillon.getId(), EtatCommande.VALIDEE);
        assertThat(ligne().getDisponible()).isEqualByComparingTo("2");

        commandeClientService.mettreAJourEtat(brouillon.getId(), EtatCommande.ANNULEE);
        assertThat(ligne().getDisponible()).isEqualByComparingTo("10");
    }

    @Test
    void une_casse_se_constate_meme_sur_du_stock_reserve() {
        commander("10");
        assertThat(ligne().getStatut()).isEqualTo(StatutStock.RUPTURE);

        mvtStkService.sortieStock(MvtStkDto.builder().article(ArticleDto.builder().Id(idArticle).build())
                .quantite(new BigDecimal("2")).motif(MotifMvtStk.CASSE).build());
        assertThat(ligne().getQuantite()).isEqualByComparingTo("8");
    }

    @Test
    void le_reappro_couvre_les_ventes_et_ne_propose_pas_deux_fois() {
        // Un achat passe : le fournisseur habituel, au sac de... carton de 10.
        ConditionnementDto palette = conditionnementService.ajouter(idArticle, ConditionnementDto.builder()
                .libelle("Palette de 10").quantiteUnites(new BigDecimal("10")).prixVenteHt(new BigDecimal("48000")).build());
        FournisseurDto cimencam = fournisseurService.save(FournisseurDto.builder().nom("Cimencam")
                .mail("cimencam" + UUID.randomUUID() + "@exemple.test").tel("690000010").build());
        CommandeFourDto achat = commandeFourService.save(CommandeFourDto.builder()
                .code("CF-" + UUID.randomUUID()).dateCommande(Instant.now()).fournisseur(cimencam)
                .ligneCmndeFournisseur(List.of(LigneCmndeFournisseurDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .conditionnement(ConditionnementDto.builder().id(palette.getId()).build())
                        .quantite(new BigDecimal("3")).prixUnitaire(new BigDecimal("40000")).build()))
                .build());
        commandeFourService.mettreAJourEtat(achat.getId(), EtatCommande.VALIDEE);
        Long idLigne = commandeFourService.lignes(achat.getId()).get(0).getId();
        LigneReceptionDto reception = new LigneReceptionDto();
        reception.setIdLigne(idLigne);
        reception.setQuantite(new BigDecimal("3"));
        commandeFourService.recevoir(achat.getId(), List.of(reception));
        // 10 + 30 en stock ; 30 vendus en trente jours : un par jour.
        vendre(idArticle, "30");

        ReapproDto proposition = reapproService.proposition();
        assertThat(proposition.getJoursCouverture()).isEqualTo(15);
        ReapproDto.LigneReappro ligne = proposition.getLignes().stream()
                .filter(l -> l.getIdArticle().equals(idArticle)).findFirst().orElseThrow();
        // Tenir 15 jours a un par jour, avec 10 disponibles : il en manque 5, soit une palette.
        assertThat(ligne.getBesoin()).isEqualByComparingTo("5");
        assertThat(ligne.getFournisseur().getNom()).isEqualTo("Cimencam");
        assertThat(ligne.getConditionnement().getId()).isEqualTo(palette.getId());
        assertThat(ligne.getQuantiteProposee()).isEqualByComparingTo("1");
        assertThat(ligne.getPrixAchat()).isEqualByComparingTo("40000");

        List<CommandeFourDto> creees = reapproService.creerLesCommandes(new CommandesReapproDto(List.of(
                new CommandesReapproDto.LigneCommandeReappro(idArticle, cimencam.getId(), palette.getId(), BigDecimal.ONE,
                        new BigDecimal("40000")))));
        assertThat(creees).singleElement().satisfies(c -> assertThat(c.getEtat()).isEqualTo(EtatCommande.EN_PREPARATION));

        // Le brouillon compte comme deja commande : l'article n'est plus propose.
        assertThat(reapproService.proposition().getLignes()).noneMatch(l -> l.getIdArticle().equals(idArticle));
    }

    @Test
    void les_classes_abc_rangent_le_chiffre_d_affaires_et_les_dormants_se_voient() {
        Long vis = article("Boîte de vis", "500");
        Long dormant = article("Peinture rose", "8000");
        entrer(vis, "10");
        entrer(dormant, "3");
        vendre(idArticle, "9");   // 45 000
        vendre(vis, "8");         // 4 000 : le ciment fait 92 % a lui seul

        AnalyseArticlesDto analyse = analyseService.articles(90, false);

        assertThat(analyse.getChiffreAffaires()).isEqualByComparingTo("49000");
        assertThat(analyse.getArticles().get(0).getIdArticle()).isEqualTo(idArticle);
        assertThat(analyse.getArticles().get(0).getClasse()).isEqualTo("A");
        assertThat(analyse.getArticles()).filteredOn(l -> l.getIdArticle().equals(vis))
                .singleElement().satisfies(l -> assertThat(l.getClasse()).isEqualTo("B"));
        assertThat(analyse.getArticles()).filteredOn(l -> l.getIdArticle().equals(dormant))
                .singleElement().satisfies(l -> {
                    assertThat(l.isDormant()).isTrue();
                    assertThat(l.getClasse()).isEqualTo("C");
                });
        assertThat(analyse.getNombreDormants()).isEqualTo(1);
    }
}
