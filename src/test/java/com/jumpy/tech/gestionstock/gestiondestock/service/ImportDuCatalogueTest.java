package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.RapportImportDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
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

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * L'import du catalogue depuis un classeur.
 *
 * C'est l'etape par laquelle un commerce entre dans l'application : un catalogue vide rend tout
 * le reste inutilisable — rien a scanner, rien a vendre, rien a compter. Elle est aussi celle ou
 * le commercant a le plus de raisons de se tromper, et la moins pardonnable : un import qui double
 * les articles ou qui tronque les codes-barres se decouvre au comptoir, devant un client.
 */
class ImportDuCatalogueTest extends AbstractIntegrationTest {

    @Autowired
    private ImportArticleService importArticleService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private EntrepriseService entrepriseService;

    private Long idEntreprise;

    @BeforeEach
    void uneEntreprise() {
        idEntreprise = entrepriseService.save(EntrepriseDto.builder()
                .nom("Quincaillerie " + UUID.randomUUID())
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .build()).getId();
        connecteChez(idEntreprise);
    }

    @AfterEach
    void oublierLUtilisateur() {
        SecurityContextHolder.clearContext();
    }

    private void connecteChez(Long entreprise) {
        UserDetailsImpl principal = new UserDetailsImpl(1L, "gerant", "gerant@exemple.test",
                "x", entreprise, List.of(new SimpleGrantedAuthority(ERole.ROLE_ADMIN.name())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    // --- fabrication de classeurs -------------------------------------------------------------

    /** Un classeur au format du modele, chaque ligne donnee cellule par cellule. */
    private MultipartFile classeur(List<List<Object>> lignes) {
        return classeurAvecEntete(
                List.of("code", "designation", "prix_ht", "taux_tva", "seuil_alerte", "categorie"),
                lignes);
    }

    private MultipartFile classeurAvecEntete(List<String> entete, List<List<Object>> lignes) {
        try (Workbook classeur = new XSSFWorkbook();
             ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {

            Sheet feuille = classeur.createSheet("Articles");
            Row premiere = feuille.createRow(0);
            for (int colonne = 0; colonne < entete.size(); colonne++) {
                premiere.createCell(colonne).setCellValue(entete.get(colonne));
            }
            for (int index = 0; index < lignes.size(); index++) {
                Row ligne = feuille.createRow(index + 1);
                List<Object> valeurs = lignes.get(index);
                for (int colonne = 0; colonne < valeurs.size(); colonne++) {
                    Object valeur = valeurs.get(colonne);
                    if (valeur == null) {
                        continue;
                    }
                    if (valeur instanceof Number nombre) {
                        ligne.createCell(colonne).setCellValue(nombre.doubleValue());
                    } else {
                        ligne.createCell(colonne).setCellValue(valeur.toString());
                    }
                }
            }
            classeur.write(sortie);
            return new MockMultipartFile("fichier", "catalogue.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    sortie.toByteArray());
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Une ligne de donnees. `List.of` refuserait les cellules vides, qui sont le cas normal. */
    private static List<Object> ligne(Object... valeurs) {
        return Arrays.asList(valeurs);
    }

    // --- le chemin nominal --------------------------------------------------------------------

    @Test
    void un_fichier_valide_remplit_le_catalogue() {
        RapportImportDto rapport = importArticleService.importer(classeur(List.of(
                ligne("CIM50", "Sac de ciment 50 kg", 4650, 19.25, 10, "CIMENT"),
                ligne("FER8", "Fer a beton 8 mm", 3200, 19.25, 5, "FER"))), false);

        assertThat(rapport.lues()).isEqualTo(2);
        assertThat(rapport.creees()).isEqualTo(2);
        assertThat(rapport.modifiees()).isZero();
        assertThat(rapport.sansRefus()).isTrue();

        ArticleDto ciment = articleService.findByCodeArticle("CIM50");
        assertThat(ciment.getDesignation()).isEqualTo("Sac de ciment 50 kg");
        assertThat(ciment.getPrixUnitaireHt()).isEqualByComparingTo("4650");
        assertThat(ciment.getIdEntreprise()).isEqualTo(idEntreprise);
    }

    @Test
    void la_categorie_absente_est_creee_plutot_que_reclamee() {
        // Exiger qu'elle existe d'abord obligerait a saisir ses categories a la main avant de
        // pouvoir importer : le mur que cet import doit abattre.
        importArticleService.importer(classeur(List.of(
                ligne("CIM50", "Sac de ciment", 4650, null, null, "CIMENT"))), false);

        assertThat(articleService.findByCodeArticle("CIM50").getCategory()).isNotNull();
    }

    // --- ce qui rend l'import rejouable -------------------------------------------------------

    @Test
    void reimporter_un_fichier_corrige_met_a_jour_au_lieu_de_doubler() {
        importArticleService.importer(classeur(List.of(
                ligne("CIM50", "Sac de ciment", 4650, null, null, null))), false);

        RapportImportDto second = importArticleService.importer(classeur(List.of(
                ligne("CIM50", "Sac de ciment 50 kg", 4800, null, null, null))), false);

        assertThat(second.creees()).isZero();
        assertThat(second.modifiees()).isEqualTo(1);
        ArticleDto ciment = articleService.findByCodeArticle("CIM50");
        assertThat(ciment.getDesignation()).isEqualTo("Sac de ciment 50 kg");
        assertThat(ciment.getPrixUnitaireHt()).isEqualByComparingTo("4800");
    }

    @Test
    void la_simulation_montre_exactement_ce_que_l_ecriture_ferait_sans_rien_ecrire() {
        MultipartFile fichier = classeur(List.of(
                ligne("CIM50", "Sac de ciment", 4650, null, null, null),
                ligne("", "Ligne sans code", 100, null, null, null)));

        RapportImportDto simule = importArticleService.importer(fichier, true);
        assertThat(simule.simulation()).isTrue();
        assertThat(simule.creees()).isEqualTo(1);
        assertThat(simule.refusees()).hasSize(1);
        // Rien n'a ete ecrit : c'est tout l'objet de la simulation.
        assertThatThrownBy(() -> articleService.findByCodeArticle("CIM50"))
                .isInstanceOf(RuntimeException.class);

        RapportImportDto ecrit = importArticleService.importer(fichier, false);
        assertThat(ecrit.creees()).isEqualTo(simule.creees());
        assertThat(ecrit.modifiees()).isEqualTo(simule.modifiees());
        assertThat(ecrit.refusees()).isEqualTo(simule.refusees());
        assertThat(articleService.findByCodeArticle("CIM50")).isNotNull();
    }

    // --- le piege du code-barres --------------------------------------------------------------

    @Test
    void un_code_barres_saisi_comme_nombre_reste_lisible() {
        // Un tableur transforme « 3017620422003 » en nombre, et une lecture naive rendrait
        // « 3.01762E+12 ». Le code ainsi tronque ne se scanne plus, et rien a l'ecran ne le dit.
        importArticleService.importer(classeur(List.of(
                ligne(3017620422003L, "Article a code-barres", 1000, null, null, null))), false);

        assertThat(articleService.findByCodeArticle("3017620422003")).isNotNull();
    }

    @Test
    void un_prix_ecrit_a_la_francaise_est_accepte() {
        // « 1 500,50 » est ce que tape quelqu'un qui compte en francais, et son tableur le laisse
        // parfois en texte. Le refuser reviendrait a refuser la moitie des fichiers pour une virgule.
        importArticleService.importer(classeur(List.of(
                ligne("CIM50", "Sac de ciment", "1 500,50", null, null, null))), false);

        assertThat(articleService.findByCodeArticle("CIM50").getPrixUnitaireHt())
                .isEqualByComparingTo(new BigDecimal("1500.50"));
    }

    // --- les refus ----------------------------------------------------------------------------

    @Test
    void une_ligne_refusee_dit_son_numero_et_sa_raison() {
        RapportImportDto rapport = importArticleService.importer(classeur(List.of(
                ligne("CIM50", "Sac de ciment", 4650, null, null, null),
                ligne("FER8", "", 3200, null, null, null))), false);

        assertThat(rapport.creees()).isEqualTo(1);
        assertThat(rapport.refusees()).singleElement().satisfies(refus -> {
            // 3 et non 1 : le numero est celui que le commercant lit dans la marge de son tableur.
            assertThat(refus.ligne()).isEqualTo(3);
            assertThat(refus.code()).isEqualTo("FER8");
            assertThat(refus.raison()).contains("Désignation");
        });
    }

    @Test
    void deux_lignes_du_meme_code_ne_se_choisissent_pas_a_la_place_du_commercant() {
        RapportImportDto rapport = importArticleService.importer(classeur(List.of(
                ligne("CIM50", "Sac de ciment", 4650, null, null, null),
                ligne("CIM50", "Sac de ciment 50 kg", 4800, null, null, null))), false);

        assertThat(rapport.creees()).isEqualTo(1);
        assertThat(rapport.refusees()).singleElement()
                .satisfies(refus -> assertThat(refus.raison()).contains("ligne 2"));
    }

    @Test
    void un_prix_negatif_ou_illisible_est_refuse() {
        RapportImportDto rapport = importArticleService.importer(classeur(List.of(
                ligne("A", "Prix negatif", -1, null, null, null),
                ligne("B", "Prix illisible", "environ mille", null, null, null),
                ligne("C", "Taux illisible", 100, "beaucoup", null, null))), false);

        assertThat(rapport.creees()).isZero();
        assertThat(rapport.refusees()).hasSize(3);
    }

    @Test
    void les_lignes_vides_du_bas_du_fichier_ne_sont_pas_des_erreurs() {
        // Un tableur en laisse derriere lui ; les compter comme refusees noierait les vraies.
        RapportImportDto rapport = importArticleService.importer(classeur(List.of(
                ligne("CIM50", "Sac de ciment", 4650, null, null, null),
                ligne("", "", null, null, null, null),
                ligne("", "", null, null, null, null))), false);

        assertThat(rapport.lues()).isEqualTo(1);
        assertThat(rapport.sansRefus()).isTrue();
    }

    // --- le fichier lui-meme ------------------------------------------------------------------

    @Test
    void un_fichier_aux_mauvaises_colonnes_est_refuse_en_bloc() {
        // Mille refus identiques cacheraient la seule chose a corriger : ce n'est pas le bon fichier.
        MultipartFile mauvais = classeurAvecEntete(
                List.of("reference", "libelle", "prix", "tva", "seuil", "famille"),
                List.of(ligne("CIM50", "Sac de ciment", 4650, null, null, null)));

        assertThatThrownBy(() -> importArticleService.importer(mauvais, true))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("colonnes");
    }

    @Test
    void un_fichier_absent_ou_illisible_est_refuse() {
        assertThatThrownBy(() -> importArticleService.importer(null, true))
                .isInstanceOf(InvalidEntityException.class);

        MultipartFile pasUnClasseur = new MockMultipartFile("fichier", "catalogue.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "ceci n'est pas un classeur".getBytes());
        assertThatThrownBy(() -> importArticleService.importer(pasUnClasseur, true))
                .isInstanceOf(InvalidEntityException.class);
    }

    @Test
    void un_compte_sans_entreprise_n_a_pas_de_catalogue_ou_importer() {
        connecteChez(null);

        assertThatThrownBy(() -> importArticleService.importer(classeur(List.of(
                ligne("CIM50", "Sac de ciment", 4650, null, null, null))), true))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("entreprise");
    }

    // --- le modele ----------------------------------------------------------------------------

    @Test
    void le_modele_produit_se_relit_lui_meme() {
        // La garantie qui compte : ce qu'on donne a remplir est accepte par ce qui lit. Un modele
        // qui derive de son lecteur se decouvre chez le premier abonne.
        MultipartFile modele = new MockMultipartFile("fichier", "modele-articles.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                importArticleService.modele());

        RapportImportDto rapport = importArticleService.importer(modele, true);

        assertThat(rapport.sansRefus()).isTrue();
        assertThat(rapport.lues()).isEqualTo(1);
    }
}
