package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneTransfertDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LotDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.RappelLotDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.SiteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.TransfertDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.MotifMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeDate;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeSite;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.lot.LotService;
import com.jumpy.tech.gestionstock.gestiondestock.promotion.Calendrier;
import com.jumpy.tech.gestionstock.gestiondestock.site.SiteCourant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Les lots d'un bout a l'autre : ils entrent avec leur date, sortent premier perime premier sorti,
 * reviennent a leur place quand une vente est annulee, voyagent avec un transfert, et se
 * retrouvent chez le client quand on les rappelle.
 */
class LotsEtPeremptionTest extends AbstractIntegrationTest {

    @Autowired
    private SiteService siteService;
    @Autowired
    private EntrepriseService entrepriseService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private MvtStkService mvtStkService;
    @Autowired
    private VenteService venteService;
    @Autowired
    private TransfertService transfertService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private LotService lotService;
    @Autowired
    private Calendrier calendrier;

    private Long idEntreprise;
    private Long magasin;
    private Long entrepot;
    private ArticleDto yaourt;
    private LocalDate aujourdhui;

    @BeforeEach
    void unYaourtSuiviParLot() {
        idEntreprise = entrepriseService.save(EntrepriseDto.builder()
                .nom("Superette " + UUID.randomUUID())
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .build()).getId();
        connecte();
        magasin = siteService.mesSites().getActif();
        entrepot = siteService.creer(SiteDto.builder().nom("Dépôt").type(TypeSite.ENTREPOT).build()).getId();
        aujourdhui = calendrier.aujourdhui();

        CategoryDto category = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID()).designation("Frais").build());
        yaourt = articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID()).designation("Yaourt nature")
                .prixUnitaireHt(new BigDecimal("250")).category(category)
                .suiviLot(true).typeDate(TypeDate.DLC).build());
    }

    @AfterEach
    void oublier() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private void connecte() {
        UserDetailsImpl principal = new UserDetailsImpl(1L, "gerant", "g@exemple.test", "x", idEntreprise,
                List.of(new SimpleGrantedAuthority(ERole.ROLE_ADMIN.name())));
        principal.setIdsSites(Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private void auSite(Long idSite) {
        MockHttpServletRequest requete = new MockHttpServletRequest();
        requete.addHeader(SiteCourant.ENTETE, String.valueOf(idSite));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requete));
    }

    private void recevoir(String numero, LocalDate date, String quantite) {
        mvtStkService.entreeStock(MvtStkDto.builder().article(ArticleDto.builder().Id(yaourt.getId()).build())
                .quantite(new BigDecimal(quantite)).idSite(magasin)
                .motif(MotifMvtStk.SAISIE_MANUELLE).numeroLot(numero).datePeremption(date).build());
    }

    private VenteDto vendre(String quantite, ClientDto client) {
        return venteService.save(VenteDto.builder()
                .code("V-" + UUID.randomUUID())
                .client(client)
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(yaourt.getId()).build())
                        .quantite(new BigDecimal(quantite)).build()))
                .build());
    }

    /** Numero de lot -> quantite, dans le site actif. */
    private Map<String, Integer> lotsIci() {
        return lotService.lotsDeLArticle(yaourt.getId()).stream()
                .filter(l -> l.getQuantite().signum() > 0)
                .collect(Collectors.toMap(LotDto::getNumero, l -> l.getQuantite().intValue()));
    }

    @Test
    void un_article_suivi_n_entre_pas_sans_son_lot_ni_sa_date() {
        assertThatThrownBy(() -> recevoir(null, null, "10"))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("numéro de lot");
        assertThatThrownBy(() -> recevoir("L1", null, "10"))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("DLC");
    }

    @Test
    void on_vend_le_premier_perime_et_l_annulation_le_remet_a_sa_place() {
        recevoir("L-TARD", aujourdhui.plusDays(40), "10");
        recevoir("L-PROCHE", aujourdhui.plusDays(5), "4");
        auSite(magasin);

        VenteDto vente = vendre("6", null);
        assertThat(lotsIci()).containsExactlyInAnyOrderEntriesOf(Map.of("L-TARD", 8));

        venteService.annuler(vente.getId());
        assertThat(lotsIci()).containsExactlyInAnyOrderEntriesOf(Map.of("L-TARD", 10, "L-PROCHE", 4));
    }

    @Test
    void un_lot_dlc_depasse_ne_se_vend_plus_et_se_sort_en_peremption() {
        recevoir("L-VIEUX", aujourdhui.minusDays(1), "5");
        auSite(magasin);

        assertThatThrownBy(() -> vendre("1", null))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("vendables");

        assertThat(lotService.peremption(false)).singleElement().satisfies(l -> {
            assertThat(l.getEtat()).isEqualTo("PERIME");
            assertThat(l.getQuantite()).isEqualByComparingTo("5");
        });

        Long idLot = lotService.peremption(false).get(0).getId();
        mvtStkService.sortieStock(MvtStkDto.builder().article(ArticleDto.builder().Id(yaourt.getId()).build())
                .quantite(new BigDecimal("5")).idLot(idLot).motif(MotifMvtStk.PEREMPTION).build());
        assertThat(lotService.peremption(false)).isEmpty();
    }

    @Test
    void le_lot_voyage_avec_le_transfert() {
        recevoir("L-TARD", aujourdhui.plusDays(40), "10");
        recevoir("L-PROCHE", aujourdhui.plusDays(5), "4");
        auSite(magasin);
        TransfertDto transfert = transfertService.creer(TransfertDto.builder()
                .idSiteDestination(entrepot)
                .lignes(List.of(LigneTransfertDto.builder()
                        .article(ArticleDto.builder().Id(yaourt.getId()).build())
                        .quantite(new BigDecimal("5")).build()))
                .build());
        transfertService.expedier(transfert.getId());
        auSite(entrepot);
        transfertService.recevoir(transfert.getId(), List.of());

        assertThat(lotsIci()).containsExactlyInAnyOrderEntriesOf(Map.of("L-PROCHE", 4, "L-TARD", 1));
        auSite(magasin);
        assertThat(lotsIci()).containsExactlyInAnyOrderEntriesOf(Map.of("L-TARD", 9));
    }

    @Test
    void le_rappel_retrouve_le_client_qui_a_achete_le_lot() {
        recevoir("L-RAPPEL", aujourdhui.plusDays(10), "6");
        ClientDto client = clientService.save(ClientDto.builder().nom("Ngo").prenoms("Marie")
                .mail("marie" + UUID.randomUUID() + "@exemple.test").numTel("690000003").build());
        auSite(magasin);
        vendre("2", client);

        Long idLot = lotService.lotsDeLArticle(yaourt.getId()).get(0).getId();
        RappelLotDto rappel = lotService.rappel(idLot);

        assertThat(rappel.lot().getQuantite()).isEqualByComparingTo("4");
        assertThat(rappel.ventes()).singleElement().satisfies(v -> {
            assertThat(v.client()).isEqualTo("Marie Ngo");
            assertThat(v.telephone()).isEqualTo("690000003");
            assertThat(v.quantite()).isEqualByComparingTo("2");
        });
        // Bientot perime : il figure dans les peremptions du magasin (30 jours par defaut).
        assertThat(lotService.peremption(false)).extracting(LotDto::getEtat).containsExactly("BIENTOT");
    }

    @Test
    void on_n_arrete_pas_le_suivi_d_un_article_dont_des_lots_sont_en_rayon() {
        recevoir("L1", aujourdhui.plusDays(10), "3");
        ArticleDto modifie = articleService.findById(yaourt.getId());
        modifie.setSuiviLot(false);

        assertThatThrownBy(() -> articleService.save(modifie))
                .isInstanceOf(InvalidEntityException.class)
                .satisfies(e -> assertThat(((InvalidEntityException) e).getErrors())
                        .anyMatch(m -> m.contains("lots en stock")));
    }
}
