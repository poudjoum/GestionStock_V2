package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.conditionnement.CodeEan;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.controller.MvtStkController;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CodeBarresDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeFourDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ConditionnementDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FactureDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCmndeFournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneFactureDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneInventaireDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneReceptionDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ResultatScanDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;
import com.jumpy.tech.gestionstock.gestiondestock.entities.MotifMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeCodeBarres;
import com.jumpy.tech.gestionstock.gestiondestock.entities.UniteMesure;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le carton et la bouteille : un meme article, un seul stock, deux facons de le vendre.
 *
 * Le stock reste tenu dans l'unite de base. Une ligne garde ce qui a ete saisi — deux cartons a
 * 9 000 — et seul le mouvement de stock convertit : deux cartons de 24 font sortir 48 bouteilles.
 */
class ConditionnementsTest extends AbstractIntegrationTest {

    private static final String EAN_BOUTEILLE = "5449000000996";

    @Autowired
    private ConditionnementService conditionnementService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private EntrepriseService entrepriseService;
    @Autowired
    private VenteService venteService;
    @Autowired
    private FactureService factureService;
    @Autowired
    private MvtStkService mvtStkService;
    @Autowired
    private StockService stockService;
    @Autowired
    private FournisseurService fournisseurService;
    @Autowired
    private CommandeFourService commandeFourService;
    @Autowired
    private com.jumpy.tech.gestionstock.gestiondestock.site.SiteCourant siteCourant;

    private Long idEntreprise;
    private CategoryDto category;
    private Long idArticle;
    private ConditionnementDto carton;
    private String eanCarton;

    @BeforeEach
    void unMagasinQuiVendAuCartonEtALaBouteille() {
        // Chaque test a son entreprise : les codes-barres sont uniques par entreprise, et le meme
        // EAN sert a tous les tests.
        idEntreprise = entrepriseService.save(EntrepriseDto.builder()
                .nom("Entreprise " + UUID.randomUUID())
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .build()).getId();
        connecte();
        category = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID()).designation("Boissons").build());
        idArticle = article("Coca-Cola 33 cl", "400", UniteMesure.PIECE);
        carton = conditionnementService.ajouter(idArticle, ConditionnementDto.builder()
                .libelle("Carton de 24").quantiteUnites(new BigDecimal("24"))
                .prixVenteHt(new BigDecimal("9000")).build());

        conditionnementService.ajouterCode(idArticle, CodeBarresDto.builder().code(EAN_BOUTEILLE).build());
        String sansCle = "1544900000099";
        eanCarton = sansCle + CodeEan.cle(sansCle);
        conditionnementService.ajouterCode(idArticle, CodeBarresDto.builder()
                .code(eanCarton).idConditionnement(carton.getId()).build());

        approvisionner(idArticle, "240");
    }

    @AfterEach
    void oublierLUtilisateur() {
        SecurityContextHolder.clearContext();
    }

    private void connecte() {
        UserDetailsImpl principal = new UserDetailsImpl(1L, "gerant", "g@exemple.test",
                "x", idEntreprise, List.of(new SimpleGrantedAuthority(ERole.ROLE_ADMIN.name())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Long article(String designation, String prix, UniteMesure unite) {
        return articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID())
                .designation(designation)
                .prixUnitaireHt(new BigDecimal(prix))
                .uniteBase(unite)
                .category(category)
                .build()).getId();
    }

    private void approvisionner(Long id, String quantite) {
        mvtStkService.entreeStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(id).build())
                .quantite(new BigDecimal(quantite)).build());
    }

    private LigneVenteDto ligne(Long id, String quantite, ConditionnementDto conditionnement) {
        return LigneVenteDto.builder()
                .article(ArticleDto.builder().Id(id).build())
                .quantite(new BigDecimal(quantite))
                .conditionnement(conditionnement == null ? null
                        : ConditionnementDto.builder().id(conditionnement.getId()).build())
                .build();
    }

    private VenteDto vendre(LigneVenteDto... lignes) {
        return venteService.save(VenteDto.builder()
                .code("VTE-" + UUID.randomUUID())
                .ligneVente(List.of(lignes))
                .build());
    }

    @Test
    void deux_cartons_vendus_font_sortir_quarante_huit_bouteilles_au_prix_du_carton() {
        VenteDto vente = vendre(ligne(idArticle, "2", carton), ligne(idArticle, "3", null));

        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("189");
        List<LigneVenteDto> lignes = venteService.lignes(vente.getId());
        LigneVenteDto enCartons = lignes.stream().filter(l -> l.getConditionnement() != null).findFirst().orElseThrow();
        assertThat(enCartons.getQuantite()).isEqualByComparingTo("2");
        assertThat(enCartons.getPrixUnitaire()).isEqualByComparingTo("9000");
        assertThat(enCartons.getContenance()).isEqualByComparingTo("24");

        FactureDto facture = factureService.emettre(vente.getId());
        LigneFactureDto ligneCarton = facture.getLignes().stream()
                .filter(l -> l.getConditionnement() != null).findFirst().orElseThrow();
        assertThat(ligneCarton.getConditionnement()).isEqualTo("Carton de 24");
        // 2 x 9 000 + 3 x 400 : le montant se calcule sur ce qui a ete saisi, sans arrondi.
        assertThat(facture.getTotalHt()).isEqualByComparingTo("19200");
    }

    @Test
    void annuler_la_vente_rend_les_bouteilles_des_cartons() {
        VenteDto vente = vendre(ligne(idArticle, "2", carton));

        venteService.annuler(vente.getId());

        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("240");
    }

    @Test
    void corriger_une_ligne_en_cartons_rattrape_des_cartons() {
        VenteDto vente = vendre(ligne(idArticle, "2", carton));
        Long idLigne = venteService.lignes(vente.getId()).get(0).getId();

        venteService.modifierQuantite(vente.getId(), idLigne, new BigDecimal("3"));

        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("168");
    }

    @Test
    void le_stock_s_oppose_a_un_carton_qu_il_ne_contient_pas() {
        assertThatThrownBy(() -> vendre(ligne(idArticle, "11", carton)))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("Stock insuffisant");
    }

    @Test
    void le_scan_reconnait_la_bouteille_le_carton_et_le_code_article() {
        ResultatScanDto bouteille = conditionnementService.scanner(EAN_BOUTEILLE);
        assertThat(bouteille.getArticle().getId()).isEqualTo(idArticle);
        assertThat(bouteille.getConditionnement()).isNull();

        ResultatScanDto leCarton = conditionnementService.scanner(eanCarton);
        assertThat(leCarton.getConditionnement().getId()).isEqualTo(carton.getId());
        assertThat(leCarton.getArticle().getConditionnements()).extracting(ConditionnementDto::getLibelle)
                .containsExactly("Carton de 24");

        String codeArticle = articleService.findById(idArticle).getCodeArticle();
        assertThat(conditionnementService.scanner(codeArticle).getArticle().getId()).isEqualTo(idArticle);

        assertThatThrownBy(() -> conditionnementService.scanner("0000000000000"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void le_catalogue_porte_les_conditionnements_et_les_codes() {
        ArticleDto lu = articleService.findAll(null, null, PageRequest.of(0, 10)).getContent().stream()
                .filter(a -> a.getId().equals(idArticle)).findFirst().orElseThrow();

        assertThat(lu.getConditionnements()).hasSize(1);
        assertThat(lu.getCodesBarres()).extracting(CodeBarresDto::getCode)
                .containsExactlyInAnyOrder(EAN_BOUTEILLE, eanCarton);
    }

    @Test
    void un_code_ne_designe_qu_une_chose_dans_le_magasin() {
        Long autre = article("Fanta 33 cl", "400", UniteMesure.PIECE);

        assertThatThrownBy(() -> conditionnementService.ajouterCode(autre,
                CodeBarresDto.builder().code(EAN_BOUTEILLE).build()))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("déjà attribué");
    }

    @Test
    void un_ean_mal_recopie_est_refuse() {
        assertThatThrownBy(() -> conditionnementService.ajouterCode(idArticle,
                CodeBarresDto.builder().code("5449000000997").type(TypeCodeBarres.EAN13).build()))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("n'est pas un EAN13 valide");
    }

    @Test
    void un_produit_sans_etiquette_recoit_un_code_interne_qui_se_scanne() {
        Long vrac = article("Savon artisanal", "500", UniteMesure.PIECE);

        CodeBarresDto code = conditionnementService.genererCodeInterne(vrac, null);

        assertThat(code.getType()).isEqualTo(TypeCodeBarres.INTERNE);
        assertThat(CodeEan.cleValide(code.getCode())).isTrue();
        assertThat(conditionnementService.scanner(code.getCode()).getArticle().getId()).isEqualTo(vrac);
    }

    @Test
    void la_piece_et_le_carton_ne_se_fractionnent_pas_le_kilo_si() {
        assertThatThrownBy(() -> vendre(ligne(idArticle, "1.5", null)))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("se compte à la pièce");
        assertThatThrownBy(() -> vendre(ligne(idArticle, "0.5", carton)))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("nombre entier");

        Long riz = article("Riz parfumé", "650", UniteMesure.KG);
        approvisionner(riz, "100");
        vendre(ligne(riz, "2.5", null));
        assertThat(mvtStkService.stockReelArticle(riz)).isEqualByComparingTo("97.5");
    }

    @Test
    void une_vente_hors_ligne_en_cartons_n_est_pas_refusee_pour_un_carton_retire_depuis() {
        conditionnementService.retirer(idArticle, carton.getId());

        assertThatThrownBy(() -> vendre(ligne(idArticle, "1", carton)))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("retiré");

        // Le carton a ete vendu avant son retrait : la vente arrive, et le stock la porte.
        venteService.synchroniser(VenteDto.builder()
                .code("VTE-" + UUID.randomUUID())
                .referenceClient(UUID.randomUUID().toString())
                .datevente(Instant.now().minusSeconds(3600))
                .ligneVente(List.of(ligne(idArticle, "1", carton)))
                .build());
        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("216");
    }

    @Test
    void retirer_un_conditionnement_libere_ses_codes() {
        conditionnementService.retirer(idArticle, carton.getId());

        assertThatThrownBy(() -> conditionnementService.scanner(eanCarton))
                .isInstanceOf(EntityNotFoundException.class);
        assertThat(conditionnementService.conditionnements(idArticle))
                .extracting(ConditionnementDto::getActif).containsExactly(false);
    }

    @Test
    void un_conditionnement_vendable_a_un_prix_et_contient_plus_d_une_unite() {
        assertThatThrownBy(() -> conditionnementService.ajouter(idArticle, ConditionnementDto.builder()
                .libelle("Pack de 6").quantiteUnites(new BigDecimal("6")).build()))
                .isInstanceOf(InvalidEntityException.class);
        assertThatThrownBy(() -> conditionnementService.ajouter(idArticle, ConditionnementDto.builder()
                .libelle("Bouteille").quantiteUnites(BigDecimal.ONE).prixVenteHt(new BigDecimal("400")).build()))
                .isInstanceOf(InvalidEntityException.class);
        assertThatThrownBy(() -> conditionnementService.ajouter(idArticle, ConditionnementDto.builder()
                .libelle("carton de 24").quantiteUnites(new BigDecimal("24")).prixVenteHt(new BigDecimal("1")).build()))
                .isInstanceOf(InvalidEntityException.class);
    }

    @Test
    void trois_cartons_recus_entrent_en_bouteilles_et_le_cout_moyen_est_celui_d_une_bouteille() {
        Long eau = article("Eau minérale 1,5 L", "300", UniteMesure.PIECE);
        ConditionnementDto pack = conditionnementService.ajouter(eau, ConditionnementDto.builder()
                .libelle("Pack de 6").quantiteUnites(new BigDecimal("6")).vendable(false).build());
        FournisseurDto fournisseur = fournisseurService.save(FournisseurDto.builder()
                .nom("Fournisseur " + UUID.randomUUID()).prenom("X")
                .mail(UUID.randomUUID() + "@exemple.test").tel("690000000").build());
        CommandeFourDto commande = commandeFourService.save(CommandeFourDto.builder()
                .code("CF-" + UUID.randomUUID())
                .fournisseur(fournisseur)
                .ligneCmndeFournisseur(List.of(LigneCmndeFournisseurDto.builder()
                        .article(ArticleDto.builder().Id(eau).build())
                        .conditionnement(ConditionnementDto.builder().id(pack.getId()).build())
                        .quantite(new BigDecimal("10"))
                        .prixUnitaire(new BigDecimal("1200"))
                        .build()))
                .build());
        commandeFourService.mettreAJourEtat(commande.getId(), EtatCommande.VALIDEE);
        LigneReceptionDto reception = new LigneReceptionDto();
        reception.setIdLigne(commandeFourService.lignes(commande.getId()).get(0).getId());
        reception.setQuantite(new BigDecimal("3"));

        commandeFourService.recevoir(commande.getId(), List.of(reception));

        assertThat(mvtStkService.stockReelArticle(eau)).isEqualByComparingTo("18");
        LigneInventaireDto ligne = stockService.inventaire(null, PageRequest.of(0, 50)).getContent().stream()
                .filter(l -> l.getIdArticle().equals(eau)).findFirst().orElseThrow();
        // 3 packs a 1 200 pour 18 bouteilles : 200 la bouteille.
        assertThat(ligne.getCoutMoyenAchat()).isEqualByComparingTo("200");
        // Le pack ne se vend pas : il ne sert qu'a l'achat.
        assertThatThrownBy(() -> vendre(ligne(eau, "1", pack)))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("ne se vend pas");
    }

    @Test
    void la_casse_se_declare_au_carton_mais_une_saisie_ne_se_fait_pas_passer_pour_une_vente() {
        MvtStkController controleur = new MvtStkController(mvtStkService, siteCourant);

        controleur.sortieStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(idArticle).build())
                .conditionnement(ConditionnementDto.builder().id(carton.getId()).build())
                .quantite(BigDecimal.ONE)
                .motif(MotifMvtStk.CASSE)
                .build());

        assertThat(mvtStkService.stockReelArticle(idArticle)).isEqualByComparingTo("216");
        assertThat(mvtStkService.mvtStkArticle(idArticle)).extracting(MvtStkDto::getMotif).contains(MotifMvtStk.CASSE);
        assertThatThrownBy(() -> controleur.sortieStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(idArticle).build())
                .quantite(BigDecimal.ONE)
                .motif(MotifMvtStk.VENTE)
                .build()))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("ne se saisit pas à la main");
    }

    @Test
    void modifier_un_article_sans_dire_son_unite_la_conserve() {
        Long riz = article("Riz brisé", "500", UniteMesure.KG);
        ArticleDto lu = articleService.findById(riz);
        lu.setUniteBase(null);
        lu.setPrixUnitaireHt(new BigDecimal("550"));

        assertThat(articleService.save(lu).getUniteBase()).isEqualTo(UniteMesure.KG);
    }
}
