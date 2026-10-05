package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ConditionnementDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCommandeClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneInventaireDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneTransfertDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MesSitesDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ReceptionTransfertDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.SiteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.StockSiteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.TransfertDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatTransfert;
import com.jumpy.tech.gestionstock.gestiondestock.entities.StatutStock;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeSite;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.site.SiteCourant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Un magasin et un entrepot : un stock par site, des transferts en deux temps, et l'entrepot qui
 * livre le client du magasin sans jamais vendre au comptoir.
 */
class SitesEtTransfertsTest extends AbstractIntegrationTest {

    @Autowired
    private SiteService siteService;
    @Autowired
    private SiteCourant siteCourant;
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
    private TransfertService transfertService;
    @Autowired
    private CommandeClientService commandeClientService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private InventaireService inventaireService;
    @Autowired
    private StockService stockService;

    private Long idEntreprise;
    private Long magasin;
    private Long entrepot;
    private Long idArticle;
    private ConditionnementDto carton;

    @BeforeEach
    void unMagasinEtUnEntrepot() {
        idEntreprise = entrepriseService.save(EntrepriseDto.builder()
                .nom("Grossiste " + UUID.randomUUID())
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .build()).getId();
        gerant();
        magasin = siteService.mesSites().getActif();
        entrepot = siteService.creer(SiteDto.builder().nom("Dépôt Bonabéri").type(TypeSite.ENTREPOT).build()).getId();

        CategoryDto category = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID()).designation("Boissons").build());
        idArticle = articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID()).designation("Coca-Cola 33 cl")
                .prixUnitaireHt(new BigDecimal("400")).category(category).build()).getId();
        carton = conditionnementService.ajouter(idArticle, ConditionnementDto.builder()
                .libelle("Carton de 24").quantiteUnites(new BigDecimal("24")).prixVenteHt(new BigDecimal("9000")).build());

        // L'arrivage va a l'entrepot : 10 cartons. Le magasin en a 12 bouteilles.
        mvtStkService.entreeStock(MvtStkDto.builder().article(ArticleDto.builder().Id(idArticle).build())
                .quantite(new BigDecimal("240")).idSite(entrepot).build());
        mvtStkService.entreeStock(MvtStkDto.builder().article(ArticleDto.builder().Id(idArticle).build())
                .quantite(new BigDecimal("12")).idSite(magasin).build());
    }

    @AfterEach
    void oublier() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private void connecte(ERole role, Set<Long> sites) {
        UserDetailsImpl principal = new UserDetailsImpl(1L, "compte", "c@exemple.test", "x", idEntreprise,
                List.of(new SimpleGrantedAuthority(role.name())));
        principal.setIdsSites(sites);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private void gerant() {
        connecte(ERole.ROLE_ADMIN, Set.of());
    }

    /** Le site choisi dans le selecteur : l'en-tete que le front envoie a chaque requete. */
    private void auSite(Long idSite) {
        MockHttpServletRequest requete = new MockHttpServletRequest();
        requete.addHeader(SiteCourant.ENTETE, String.valueOf(idSite));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requete));
    }

    private BigDecimal stock(Long idSite) {
        return mvtStkService.stockReelDansSite(idArticle, idSite);
    }

    @Test
    void chaque_site_a_son_stock_et_l_on_voit_ou_est_la_marchandise() {
        assertThat(stock(entrepot)).isEqualByComparingTo("240");
        assertThat(stock(magasin)).isEqualByComparingTo("12");

        assertThat(mvtStkService.stocksParSite(idArticle))
                .extracting(StockSiteDto::nomSite, s -> s.quantite().intValue())
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Magasin principal", 12),
                        org.assertj.core.groups.Tuple.tuple("Dépôt Bonabéri", 240));

        LigneInventaireDto tous = stockService.inventaire(null, PageRequest.of(0, 10), true).getContent().get(0);
        assertThat(tous.getQuantite()).isEqualByComparingTo("252");
        assertThat(tous.getParSite()).hasSize(2);
    }

    @Test
    void le_magasin_vend_ce_qu_il_a_et_non_ce_qu_a_l_entrepot() {
        auSite(magasin);
        assertThatThrownBy(() -> vendre("13"))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("Magasin principal");

        vendre("12");
        assertThat(stock(magasin)).isEqualByComparingTo("0");
        assertThat(stock(entrepot)).isEqualByComparingTo("240");
    }

    @Test
    void un_entrepot_ne_vend_pas_au_comptoir() {
        auSite(entrepot);
        assertThatThrownBy(() -> vendre("1"))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("entrepôt");
    }

    private VenteDto vendre(String quantite) {
        return venteService.save(VenteDto.builder()
                .code("V-" + UUID.randomUUID())
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal(quantite)).build()))
                .build());
    }

    @Test
    void le_transfert_sort_a_l_expedition_et_entre_a_la_reception_ce_qui_est_compte() {
        auSite(entrepot);
        TransfertDto transfert = transfertService.creer(TransfertDto.builder()
                .idSiteDestination(magasin)
                .lignes(List.of(LigneTransfertDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .conditionnement(ConditionnementDto.builder().id(carton.getId()).build())
                        .quantite(new BigDecimal("3")).build()))
                .build());
        assertThat(transfert.getEtat()).isEqualTo(EtatTransfert.BROUILLON);
        // Un brouillon ne fait rien bouger.
        assertThat(stock(entrepot)).isEqualByComparingTo("240");

        transfertService.expedier(transfert.getId());
        // En transit : sorti de l'entrepot, pas encore au magasin.
        assertThat(stock(entrepot)).isEqualByComparingTo("168");
        assertThat(stock(magasin)).isEqualByComparingTo("12");

        Long idLigne = transfert.getLignes().get(0).getId();
        auSite(magasin);
        // Un carton manque : il faut dire pourquoi.
        assertThatThrownBy(() -> transfertService.recevoir(transfert.getId(),
                List.of(new ReceptionTransfertDto(idLigne, new BigDecimal("2"), null))))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("dites pourquoi");
        assertThatThrownBy(() -> transfertService.recevoir(transfert.getId(),
                List.of(new ReceptionTransfertDto(idLigne, new BigDecimal("4"), null))))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("plus");

        TransfertDto recu = transfertService.recevoir(transfert.getId(),
                List.of(new ReceptionTransfertDto(idLigne, new BigDecimal("2"), "Carton écrasé au chargement")));

        assertThat(recu.getEtat()).isEqualTo(EtatTransfert.RECU);
        assertThat(recu.getLignes().get(0).getMotifEcart()).isEqualTo("Carton écrasé au chargement");
        assertThat(stock(magasin)).isEqualByComparingTo("60");
        assertThat(stock(entrepot)).isEqualByComparingTo("168");
    }

    @Test
    void on_ne_charge_pas_ce_que_le_depot_n_a_pas() {
        auSite(magasin);
        TransfertDto transfert = transfertService.creer(TransfertDto.builder()
                .idSiteDestination(entrepot)
                .lignes(List.of(LigneTransfertDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal("13")).build()))
                .build());

        assertThatThrownBy(() -> transfertService.expedier(transfert.getId()))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("Stock insuffisant");
        assertThat(transfertService.detail(transfert.getId()).getEtat()).isEqualTo(EtatTransfert.BROUILLON);
    }

    @Test
    void l_entrepot_livre_le_client_du_magasin_sur_commande() {
        ClientDto client = clientService.save(ClientDto.builder().nom("Ngo").prenoms("Marie")
                .mail("marie" + UUID.randomUUID() + "@exemple.test").numTel("690000003").build());
        auSite(magasin);
        CommandeClientDto commande = commandeClientService.save(CommandeClientDto.builder()
                .code("CC-" + UUID.randomUUID())
                .client(client)
                .idSiteExpedition(entrepot)
                .ligneCmndeClients(List.of(LigneCommandeClientDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .conditionnement(ConditionnementDto.builder().id(carton.getId()).build())
                        .quantite(new BigDecimal("5")).build()))
                .build());
        assertThat(commande.getNomSite()).isEqualTo("Magasin principal");
        assertThat(commande.getNomSiteExpedition()).isEqualTo("Dépôt Bonabéri");
        commandeClientService.mettreAJourEtat(commande.getId(), EtatCommande.VALIDEE);

        VenteDto vente = venteService.servirCommandeClient(commande.getId());

        // La vente est celle du magasin ; les cinq cartons sont partis de l'entrepot.
        assertThat(vente.getIdSite()).isEqualTo(magasin);
        assertThat(vente.getIdSiteExpedition()).isEqualTo(entrepot);
        assertThat(stock(entrepot)).isEqualByComparingTo("120");
        assertThat(stock(magasin)).isEqualByComparingTo("12");
    }

    @Test
    void la_commande_se_prend_au_magasin_pas_a_l_entrepot() {
        ClientDto client = clientService.save(ClientDto.builder().nom("Ngo").prenoms("Paul")
                .mail("paul" + UUID.randomUUID() + "@exemple.test").numTel("690000004").build());
        auSite(entrepot);
        assertThatThrownBy(() -> commandeClientService.save(CommandeClientDto.builder()
                .code("CC-" + UUID.randomUUID()).client(client)
                .ligneCmndeClients(List.of(LigneCommandeClientDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(BigDecimal.ONE).build()))
                .build()))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("entrepôt");
    }

    @Test
    void un_collaborateur_ne_travaille_que_dans_ses_sites() {
        connecte(ERole.ROLE_MAGASINIER, Set.of(entrepot));

        MesSitesDto siens = siteService.mesSites();
        assertThat(siens.getSites()).extracting(SiteDto::getId).containsExactly(entrepot);
        assertThat(siens.getActif()).isEqualTo(entrepot);
        assertThat(siens.isTousLesSites()).isFalse();

        assertThatThrownBy(() -> siteCourant.accessible(magasin)).isInstanceOf(EntityNotFoundException.class);
        // Demander « tous les sites » ne lui montre que le sien.
        assertThat(stockService.inventaire(null, PageRequest.of(0, 10), true).getContent().get(0).getQuantite())
                .isEqualByComparingTo("240");
    }

    @Test
    void chaque_site_se_compte_a_son_tour_et_se_corrige_chez_lui() {
        auSite(magasin);
        var auMagasin = inventaireService.ouvrir("Magasin");
        auSite(entrepot);
        var aLEntrepot = inventaireService.ouvrir("Dépôt");
        assertThat(aLEntrepot.nomSite()).isEqualTo("Dépôt Bonabéri");

        inventaireService.compter(aLEntrepot.id(), idArticle, new BigDecimal("230"));
        inventaireService.valider(aLEntrepot.id());

        assertThat(stock(entrepot)).isEqualByComparingTo("230");
        assertThat(stock(magasin)).isEqualByComparingTo("12");
        auSite(magasin);
        assertThat(inventaireService.seanceOuverte().id()).isEqualTo(auMagasin.id());
    }

    @Test
    void le_seuil_d_un_site_l_emporte_sur_celui_de_l_article() {
        siteService.definirSeuil(idArticle, magasin, new BigDecimal("24"));
        auSite(magasin);

        assertThat(stockService.alertes(false)).extracting(LigneInventaireDto::getStatut)
                .containsExactly(StatutStock.SOUS_SEUIL);
        auSite(entrepot);
        assertThat(stockService.alertes(false)).isEmpty();
    }

    @Test
    void un_site_qui_a_du_stock_ne_se_ferme_pas_et_le_principal_jamais() {
        assertThatThrownBy(() -> siteService.fermer(entrepot))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("encore du stock");
        assertThatThrownBy(() -> siteService.fermer(magasin))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("principal");

        Long vide = siteService.creer(SiteDto.builder().nom("Boutique Akwa").type(TypeSite.MAGASIN).build()).getId();
        assertThat(siteService.fermer(vide).getActif()).isFalse();
    }
}
