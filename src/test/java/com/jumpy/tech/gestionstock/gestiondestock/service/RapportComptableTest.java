package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FactureDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ReglementDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ModeReglement;
import com.jumpy.tech.gestionstock.gestiondestock.promotion.Calendrier;
import com.jumpy.tech.gestionstock.gestiondestock.rapport.ExportComptable;
import com.jumpy.tech.gestionstock.gestiondestock.rapport.RapportComptableDto;
import com.jumpy.tech.gestionstock.gestiondestock.rapport.RapportComptableService;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EntrepriseRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** R3 : la TVA par taux, les journaux, et le classeur du comptable. */
class RapportComptableTest extends AbstractIntegrationTest {

    @Autowired
    private EntrepriseService entrepriseService;
    @Autowired
    private EntrepriseRepository entrepriseRepository;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private MvtStkService mvtStkService;
    @Autowired
    private VenteService venteService;
    @Autowired
    private FactureService factureService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private RapportComptableService rapportComptableService;
    @Autowired
    private ExportComptable exportComptable;
    @Autowired
    private Calendrier calendrier;

    private Long idEntreprise;
    private Long taxe;
    private Long exonere;
    private LocalDate aujourdhui;

    @BeforeEach
    void deuxArticles() {
        idEntreprise = entrepriseService.save(EntrepriseDto.builder()
                .nom("Épicerie Akwa " + UUID.randomUUID().toString().substring(0, 6))
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .build()).getId();
        UserDetailsImpl principal = new UserDetailsImpl(1L, "comptable", "c@exemple.test", "x", idEntreprise,
                List.of(new SimpleGrantedAuthority(ERole.ROLE_ADMIN.name())));
        principal.setIdsSites(Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        aujourdhui = calendrier.aujourdhui();

        CategoryDto rayon = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID()).designation("Divers").build());
        taxe = article(rayon, "Savon", "1000", "19.25");
        exonere = article(rayon, "Riz local", "2000", "0");
    }

    @AfterEach
    void oublier() {
        SecurityContextHolder.clearContext();
    }

    private Long article(CategoryDto rayon, String nom, String prix, String tva) {
        Long id = articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID()).designation(nom)
                .prixUnitaireHt(new BigDecimal(prix)).tauxTva(new BigDecimal(tva)).category(rayon).build()).getId();
        mvtStkService.entreeStock(MvtStkDto.builder().article(ArticleDto.builder().Id(id).build())
                .quantite(new BigDecimal("100")).build());
        return id;
    }

    private FactureDto facturer(ClientDto client, Long idArticle, String quantite) {
        VenteDto vente = venteService.save(VenteDto.builder()
                .code("V-" + UUID.randomUUID())
                .client(client)
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal(quantite)).build()))
                .build());
        return factureService.emettre(vente.getId());
    }

    @Test
    void la_tva_se_lit_par_taux_et_les_journaux_gardent_les_annulees() {
        ClientDto client = clientService.save(ClientDto.builder().nom("Ngo").prenoms("Marie")
                .mail("marie" + UUID.randomUUID() + "@exemple.test").numTel("690000020").build());
        FactureDto f1 = facturer(client, taxe, "2");      // 2 000 HT, 385 TVA
        FactureDto f2 = facturer(null, exonere, "3");     // 6 000 HT, 0 TVA
        FactureDto annulee = facturer(null, taxe, "1");
        factureService.annuler(annulee.getId());
        factureService.regler(f1.getId(), ReglementDto.builder().montant(new BigDecimal("1000")).mode(ModeReglement.MOBILE_MONEY).build());
        factureService.regler(f2.getId(), ReglementDto.builder().montant(f2.getTotalTtc()).mode(ModeReglement.ESPECES).build());

        RapportComptableDto r = rapportComptableService.comptabilite(aujourdhui, aujourdhui, false);

        assertThat(r.getTva()).extracting(RapportComptableDto.LigneTva::getLibelle)
                .containsExactly("TVA 19,25 %", "Exonéré (0 %)");
        assertThat(r.getTotalHt()).isEqualByComparingTo("8000");
        assertThat(r.getTotalTva()).isEqualByComparingTo("385");
        assertThat(r.getFactures()).isEqualTo(2);
        assertThat(r.getFacturesAnnulees()).isEqualTo(1);
        assertThat(r.getJournalVentes()).hasSize(3);
        assertThat(r.getJournalVentes()).filteredOn(RapportComptableDto.LigneJournalVentes::isAnnulee)
                .singleElement().satisfies(v -> assertThat(v.getTotalTtc()).isEqualByComparingTo("0"));
        assertThat(r.getJournalVentes()).filteredOn(v -> v.getNumero().equals(f1.getNumero()))
                .singleElement().satisfies(v -> {
                    assertThat(v.getClient()).isEqualTo("Marie Ngo");
                    assertThat(v.getReste()).isEqualByComparingTo("1385");
                });
        assertThat(r.getTotalEncaisse()).isEqualByComparingTo(new BigDecimal("1000").add(f2.getTotalTtc()));
        assertThat(r.getEncaissementsParMode()).extracting(RapportComptableDto.EncaissementParMode::getLibelle)
                .containsExactly("Espèces", "Mobile money");
    }

    @Test
    void le_classeur_est_type_totalise_et_pret_a_imprimer() throws Exception {
        facturer(null, taxe, "2");
        FactureDto f2 = facturer(null, exonere, "3");
        factureService.regler(f2.getId(), ReglementDto.builder().montant(f2.getTotalTtc()).mode(ModeReglement.ESPECES).build());
        factureService.annuler(facturer(null, taxe, "1").getId());
        RapportComptableDto r = rapportComptableService.comptabilite(aujourdhui, aujourdhui, false);
        var entreprise = entrepriseRepository.findById(idEntreprise).orElseThrow();

        byte[] octets = exportComptable.classeur(r, entreprise, calendrier.fuseau());

        try (XSSFWorkbook classeur = new XSSFWorkbook(new ByteArrayInputStream(octets))) {
            assertThat(classeur.getNumberOfSheets()).isEqualTo(3);
            assertThat(classeur.getSheetName(0)).isEqualTo("Synthèse");
            assertThat(classeur.getSheetName(1)).isEqualTo("Journal des ventes");
            assertThat(classeur.getSheetName(2)).isEqualTo("Encaissements");

            XSSFSheet journal = classeur.getSheet("Journal des ventes");
            assertThat(journal.getRow(0).getCell(0).getStringCellValue()).isEqualTo(entreprise.getNom());
            assertThat(journal.getRow(2).getCell(0).getStringCellValue()).contains("Magasin principal");
            // Les titres en ligne 5, figes, filtres.
            assertThat(journal.getRow(4).getCell(3).getStringCellValue()).isEqualTo("Total HT (FCFA)");
            assertThat(journal.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 5);
            assertThat(journal.getCTWorksheet().isSetAutoFilter()).isTrue();

            Row premiere = journal.getRow(5);
            assertThat(premiere.getCell(0).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(DateUtil.isCellDateFormatted(premiere.getCell(0))).isTrue();
            assertThat(premiere.getCell(3).getCellType()).isEqualTo(CellType.NUMERIC);

            Row annulee = ligneOu(journal, 8, "Annulée");
            assertThat(annulee).isNotNull();
            assertThat(annulee.getCell(5).getNumericCellValue()).isZero();

            // Le total en formule, qui retombe sur le total du rapport.
            Row total = ligneOu(journal, 0, "Total");
            Cell totalHt = total.getCell(3);
            assertThat(totalHt.getCellType()).isEqualTo(CellType.FORMULA);
            FormulaEvaluator evaluateur = classeur.getCreationHelper().createFormulaEvaluator();
            assertThat(evaluateur.evaluate(totalHt).getNumberValue()).isEqualTo(r.getTotalHt().doubleValue());

            assertThat(journal.getPrintSetup().getLandscape()).isTrue();
            assertThat(journal.getFooter().getRight()).contains("&P");
        }

        assertThat(ExportComptable.nomDeFichier(entreprise, RapportComptableDto.builder()
                .debut(LocalDate.of(2026, 9, 1)).fin(LocalDate.of(2026, 9, 30)).build()))
                .startsWith("Comptabilite_Epicerie-Akwa-").endsWith("_2026-09.xlsx");
    }

    private static Row ligneOu(Sheet feuille, int colonne, String texte) {
        for (Row row : feuille) {
            Cell cell = row.getCell(colonne);
            if (cell != null && cell.getCellType() == CellType.STRING && texte.equals(cell.getStringCellValue())) {
                return row;
            }
        }
        return null;
    }
}
