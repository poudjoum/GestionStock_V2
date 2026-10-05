package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ConditionnementDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.RapportImportDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.UniteMesure;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L'import des unites et des conditionnements.
 *
 * Un grossiste qui s'installe a six cents references, et autant de cartons : les saisir un par un
 * sur la fiche serait le mur que l'import du catalogue a deja abattu pour les articles.
 */
class ImportDesConditionnementsTest extends AbstractIntegrationTest {

    private static final List<String> ARTICLES =
            List.of("code", "designation", "prix_ht", "taux_tva", "seuil_alerte", "categorie", "unite");
    private static final List<String> CONDITIONNEMENTS =
            List.of("code_article", "libelle", "contenance", "prix_ht", "code_barres");

    @Autowired
    private ImportArticleService importArticleService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private ConditionnementService conditionnementService;
    @Autowired
    private EntrepriseService entrepriseService;

    @BeforeEach
    void uneEntreprise() {
        Long idEntreprise = entrepriseService.save(EntrepriseDto.builder()
                .nom("Grossiste " + UUID.randomUUID())
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .build()).getId();
        UserDetailsImpl principal = new UserDetailsImpl(1L, "gerant", "gerant@exemple.test",
                "x", idEntreprise, List.of(new SimpleGrantedAuthority(ERole.ROLE_ADMIN.name())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void oublierLUtilisateur() {
        SecurityContextHolder.clearContext();
    }

    private static List<Object> ligne(Object... valeurs) {
        return Arrays.asList(valeurs);
    }

    private MultipartFile classeur(List<List<Object>> articles, List<List<Object>> conditionnements) {
        try (Workbook classeur = new XSSFWorkbook();
             ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            remplir(classeur.createSheet("Articles"), ARTICLES, articles);
            if (conditionnements != null) {
                remplir(classeur.createSheet("Conditionnements"), CONDITIONNEMENTS, conditionnements);
            }
            classeur.write(sortie);
            return new MockMultipartFile("fichier", "catalogue.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", sortie.toByteArray());
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void remplir(Sheet feuille, List<String> entete, List<List<Object>> lignes) {
        Row premiere = feuille.createRow(0);
        for (int c = 0; c < entete.size(); c++) {
            premiere.createCell(c).setCellValue(entete.get(c));
        }
        for (int i = 0; i < lignes.size(); i++) {
            Row ligne = feuille.createRow(i + 1);
            List<Object> valeurs = lignes.get(i);
            for (int c = 0; c < valeurs.size(); c++) {
                Object v = valeurs.get(c);
                if (v instanceof Number n) {
                    ligne.createCell(c).setCellValue(n.doubleValue());
                } else if (v != null) {
                    ligne.createCell(c).setCellValue(v.toString());
                }
            }
        }
    }

    @Test
    void les_unites_et_les_cartons_entrent_avec_les_articles() {
        RapportImportDto rapport = importArticleService.importer(classeur(
                List.of(ligne("COCA33", "Coca-Cola 33 cl", 400, null, null, "BOISSONS", "pièce"),
                        ligne("RIZ", "Riz parfumé", 650, null, null, "EPICERIE", "Kilo")),
                List.of(ligne("COCA33", "Carton de 24", 24, 9000, 15449000000993L),
                        ligne("RIZ", "Sac de 25 kg", 25, 15000, null),
                        ligne("RIZ", "Palette", 1000, null, null))), false);

        assertThat(rapport.refusees()).isEmpty();
        assertThat(rapport.creees()).isEqualTo(2);
        assertThat(rapport.conditionnements()).isEqualTo(3);

        ArticleDto riz = articleService.findByCodeArticle("RIZ");
        assertThat(riz.getUniteBase()).isEqualTo(UniteMesure.KG);
        // Sans prix, la palette ne sert qu'a l'achat.
        assertThat(riz.getConditionnements()).extracting(ConditionnementDto::getLibelle, ConditionnementDto::getVendable)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Sac de 25 kg", true),
                        org.assertj.core.groups.Tuple.tuple("Palette", false));

        ArticleDto coca = articleService.findByCodeArticle("COCA33");
        assertThat(conditionnementService.scanner("15449000000993").getArticle().getId()).isEqualTo(coca.getId());
    }

    @Test
    void reimporter_reprend_le_carton_au_lieu_de_le_doubler() {
        importArticleService.importer(classeur(
                List.of(ligne("COCA33", "Coca-Cola 33 cl", 400, null, null, null, null)),
                List.of(ligne("COCA33", "Carton de 24", 24, 9000, null))), false);
        importArticleService.importer(classeur(
                List.of(ligne("COCA33", "Coca-Cola 33 cl", 400, null, null, null, null)),
                List.of(ligne("COCA33", "carton de 24", 24, 8800, null))), false);

        List<ConditionnementDto> cartons = conditionnementService.conditionnements(
                articleService.findByCodeArticle("COCA33").getId());
        assertThat(cartons).hasSize(1);
        assertThat(cartons.get(0).getPrixVenteHt()).isEqualByComparingTo("8800");
    }

    @Test
    void une_ligne_fausse_est_refusee_sans_empecher_les_autres() {
        RapportImportDto rapport = importArticleService.importer(classeur(
                List.of(ligne("COCA33", "Coca-Cola 33 cl", 400, null, null, null, "pièce"),
                        ligne("SAC", "Sac", 100, null, null, null, "tonneau")),
                List.of(ligne("COCA33", "Carton de 24", 24, 9000, null),
                        ligne("COCA33", "Demi-carton", 12.5, 4500, null),
                        ligne("INCONNU", "Carton", 12, 4500, null),
                        ligne("COCA33", "Pack", 6, 2300, 5449000000997L))), false);

        assertThat(rapport.creees()).isEqualTo(1);
        assertThat(rapport.conditionnements()).isEqualTo(1);
        assertThat(rapport.refusees()).extracting(RapportImportDto.LigneRefuseeDto::raison)
                .anySatisfy(r -> assertThat(r).startsWith("Unité inconnue"))
                .anySatisfy(r -> assertThat(r).contains("nombre entier"))
                .anySatisfy(r -> assertThat(r).contains("Aucun article ne porte le code INCONNU"))
                .anySatisfy(r -> assertThat(r).contains("clé de contrôle fausse"));
    }

    @Test
    void la_simulation_compte_les_cartons_d_articles_encore_absents_sans_rien_ecrire() {
        RapportImportDto rapport = importArticleService.importer(classeur(
                List.of(ligne("NEUF", "Article neuf", 400, null, null, null, null)),
                List.of(ligne("NEUF", "Carton de 12", 12, 4500, null))), true);

        assertThat(rapport.conditionnements()).isEqualTo(1);
        assertThat(rapport.refusees()).isEmpty();
        assertThat(articleService.findAll()).extracting(ArticleDto::getCodeArticle).doesNotContain("NEUF");
    }

    @Test
    void un_fichier_d_avant_les_unites_reste_accepte_et_ne_change_pas_l_unite() {
        importArticleService.importer(classeur(
                List.of(ligne("RIZ", "Riz", 650, null, null, null, "kg")), null), false);
        // L'ancien modele : six colonnes, pas d'unite.
        try (Workbook classeur = new XSSFWorkbook(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            remplir(classeur.createSheet("Articles"), ARTICLES.subList(0, 6),
                    List.of(ligne("RIZ", "Riz parfumé", 700, null, null, null)));
            classeur.write(sortie);
            importArticleService.importer(new MockMultipartFile("fichier", "ancien.xlsx", null, sortie.toByteArray()), false);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }

        ArticleDto riz = articleService.findByCodeArticle("RIZ");
        assertThat(riz.getUniteBase()).isEqualTo(UniteMesure.KG);
        assertThat(riz.getPrixUnitaireHt()).isEqualByComparingTo("700");
    }

    @Test
    void le_modele_montre_l_unite_et_la_feuille_des_conditionnements() throws Exception {
        try (Workbook modele = WorkbookFactory.create(new ByteArrayInputStream(importArticleService.modele()))) {
            assertThat(modele.getSheetAt(0).getRow(0).getCell(6).getStringCellValue()).isEqualTo("unite");
            Sheet conditionnements = modele.getSheet("Conditionnements");
            assertThat(conditionnements).isNotNull();
            assertThat(conditionnements.getRow(0).getCell(4).getStringCellValue()).isEqualTo("code_barres");
        }
    }
}
