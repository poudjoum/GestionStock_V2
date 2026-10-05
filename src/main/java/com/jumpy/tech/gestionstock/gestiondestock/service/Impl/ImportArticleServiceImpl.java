package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.conditionnement.CodeEan;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.RapportImportDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.RapportImportDto.LigneRefuseeDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Category;
import com.jumpy.tech.gestionstock.gestiondestock.entities.CodeBarres;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Conditionnement;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeCodeBarres;
import com.jumpy.tech.gestionstock.gestiondestock.entities.UniteMesure;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.CategoryRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.CodeBarresRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ConditionnementRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.ImportArticleService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Voir {@link ImportArticleService}. */
@Service
@Slf4j
public class ImportArticleServiceImpl implements ImportArticleService {

    /**
     * Les colonnes du modele, dans l'ordre.
     *
     * Imposees, et c'est ce qui permet de refuser un fichier proprement plutot que de deviner ce
     * que le commercant a voulu mettre dans sa troisieme colonne. Un ecran de correspondance
     * viendra si les premiers abonnes butent dessus.
     */
    private static final List<String> COLONNES =
            List.of("code", "designation", "prix_ht", "taux_tva", "seuil_alerte", "categorie");

    private static final int CODE = 0;
    private static final int DESIGNATION = 1;
    private static final int PRIX_HT = 2;
    private static final int TAUX_TVA = 3;
    private static final int SEUIL = 4;
    private static final int CATEGORIE = 5;

    /**
     * Facultative, apres les six du modele : un fichier fait avant les unites reste accepte tel
     * quel, et ses articles restent a la piece.
     */
    private static final String COLONNE_UNITE = "unite";
    private static final int UNITE = 6;

    /**
     * La seconde feuille, facultative elle aussi : les conditionnements des articles, un par ligne.
     * Sans prix, le conditionnement ne sert qu'a l'achat — la palette qu'on recoit et ne vend pas.
     */
    private static final String FEUILLE_CONDITIONNEMENTS = "conditionnements";
    private static final List<String> COLONNES_CONDITIONNEMENTS =
            List.of("code_article", "libelle", "contenance", "prix_ht", "code_barres");

    /** Au-dela, ce n'est plus le catalogue d'un petit commerce mais un fichier envoye par erreur. */
    private static final int LIGNES_MAX = 10_000;

    private final ArticleRepository articleRepository;
    private final CategoryRepository categoryRepository;
    private final Cloisonnement cloisonnement;
    private final ConditionnementRepository conditionnementRepository;
    private final CodeBarresRepository codeBarresRepository;

    public ImportArticleServiceImpl(ArticleRepository articleRepository,
                                    CategoryRepository categoryRepository,
                                    Cloisonnement cloisonnement,
                                    ConditionnementRepository conditionnementRepository,
                                    CodeBarresRepository codeBarresRepository) {
        this.conditionnementRepository = conditionnementRepository;
        this.codeBarresRepository = codeBarresRepository;
        this.articleRepository = articleRepository;
        this.categoryRepository = categoryRepository;
        this.cloisonnement = cloisonnement;
    }

    @Override
    @Transactional
    public RapportImportDto importer(MultipartFile fichier, boolean simulation) {
        Long entreprise = entrepriseOuRefus();
        if (fichier == null || fichier.isEmpty()) {
            throw new InvalidEntityException("Aucun fichier n'a été reçu", ErrorCodes.IMPORT_NOT_VALID);
        }

        try (InputStream flux = fichier.getInputStream();
             Workbook classeur = WorkbookFactory.create(flux)) {

            Sheet feuille = classeur.getSheetAt(0);
            FormulaEvaluator calcul = classeur.getCreationHelper().createFormulaEvaluator();
            boolean avecUnite = verifierEntete(feuille, calcul);

            Map<String, UniteMesure> importes = new HashMap<>();
            RapportImportDto articles = parcourir(feuille, calcul, entreprise, simulation, avecUnite, importes);

            Sheet conditionnements = feuilleConditionnements(classeur);
            if (conditionnements == null) {
                return articles;
            }
            return parcourirConditionnements(conditionnements, calcul, entreprise, simulation, importes, articles);

        } catch (IOException | RuntimeException echec) {
            if (echec instanceof InvalidEntityException connue) {
                throw connue;
            }
            // Un classeur corrompu, chiffre, ou un fichier qui n'en est pas un : le message de la
            // bibliotheque ne dit rien a un commercant, mais il aide au support.
            log.warn("Classeur illisible a l'import : {}", echec.getMessage());
            throw new InvalidEntityException(
                    "Ce fichier n'a pas pu être lu. Attendu : un classeur Excel (.xlsx) au format du modèle.",
                    ErrorCodes.IMPORT_NOT_VALID);
        }
    }

    /**
     * L'entreprise du compte, sans laquelle l'import n'a pas de destination.
     *
     * Un super-administrateur n'appartient a aucune maison : importer sous son compte poserait des
     * articles que le cloisonnement ne rattacherait a personne, et qu'aucun commercant ne verrait.
     */
    private Long entrepriseOuRefus() {
        Long entreprise = cloisonnement.entrepriseCourante();
        if (entreprise == null) {
            throw new InvalidEntityException(
                    "Ce compte n'est rattaché à aucune entreprise : il n'y a pas de catalogue où importer",
                    ErrorCodes.IMPORT_NOT_VALID);
        }
        return entreprise;
    }

    /**
     * Le fichier porte-t-il bien les colonnes du modele ?
     *
     * Verifie avant d'examiner la moindre ligne : un fichier aux mauvaises colonnes produirait
     * mille refus identiques, ce qui cache la seule chose a corriger — le fichier n'est pas le bon.
     */
    private boolean verifierEntete(Sheet feuille, FormulaEvaluator calcul) {
        Row entete = feuille == null ? null : feuille.getRow(feuille.getFirstRowNum());
        if (entete == null) {
            throw new InvalidEntityException("Le fichier est vide", ErrorCodes.IMPORT_NOT_VALID);
        }

        List<String> lues = new ArrayList<>();
        for (int colonne = 0; colonne < COLONNES.size(); colonne++) {
            lues.add(sansAccentNiCasse(texte(entete.getCell(colonne), calcul)));
        }
        if (!lues.equals(COLONNES)) {
            throw new InvalidEntityException(
                    "Les colonnes du fichier ne sont pas celles du modèle. Attendu : "
                            + String.join(", ", COLONNES) + ". Lu : " + String.join(", ", lues),
                    ErrorCodes.IMPORT_NOT_VALID);
        }
        return COLONNE_UNITE.equals(sansAccentNiCasse(texte(entete.getCell(UNITE), calcul)));
    }

    /**
     * Les articles du fichier, ecrits ou simules. `importes` recoit le code et l'unite de chaque
     * ligne acceptee : la feuille des conditionnements s'y refere, y compris en simulation, ou les
     * articles nouveaux ne sont pas encore en base.
     */
    private RapportImportDto parcourir(Sheet feuille, FormulaEvaluator calcul,
                                       Long entreprise, boolean simulation, boolean avecUnite,
                                       Map<String, UniteMesure> importes) {
        List<LigneRefuseeDto> refusees = new ArrayList<>();
        // Les codes deja vus dans CE fichier, avec la ligne ou ils sont apparus. Deux lignes du
        // meme code, ce sont deux verites pour un seul article : on ne choisit pas a la place du
        // commercant.
        Map<String, Integer> dejaVus = new HashMap<>();
        int lues = 0;
        int creees = 0;
        int modifiees = 0;

        int premiere = feuille.getFirstRowNum() + 1;
        for (int index = premiere; index <= feuille.getLastRowNum(); index++) {
            Row ligne = feuille.getRow(index);
            if (vide(ligne, calcul)) {
                // Un tableur laisse derriere lui des lignes vides qu'on n'a pas saisies : les
                // compter comme refusees noierait les vraies erreurs.
                continue;
            }
            int numero = index + 1; // ce que le commercant lit dans la marge de son tableur
            lues++;
            if (lues > LIGNES_MAX) {
                throw new InvalidEntityException(
                        "Le fichier dépasse " + LIGNES_MAX + " lignes", ErrorCodes.IMPORT_NOT_VALID);
            }

            String code = texte(ligne.getCell(CODE), calcul);
            String refus = raisonDeRefus(ligne, calcul, code, dejaVus, numero);
            String saisieUnite = avecUnite ? texte(ligne.getCell(UNITE), calcul) : "";
            UniteMesure unite = saisieUnite.isEmpty() ? null : unite(saisieUnite);
            if (refus == null && !saisieUnite.isEmpty() && unite == null) {
                refus = "Unité inconnue : « " + saisieUnite + " ». Attendu : pièce, kg, litre, mètre, m2 ou m3";
            }
            if (refus != null) {
                refusees.add(new LigneRefuseeDto(numero, code.isEmpty() ? null : code, refus));
                continue;
            }
            dejaVus.put(code, numero);

            Optional<Article> existant =
                    articleRepository.findArticleByCodeArticleAndIdEntreprise(code, entreprise);
            // Sans unite dans le fichier, celle de l'article reste : un reimport ne remet pas le
            // riz a la piece.
            UniteMesure retenue = unite != null ? unite
                    : existant.map(Article::getUniteBase).orElse(UniteMesure.PIECE);
            importes.put(code, retenue);
            if (existant.isPresent()) {
                modifiees++;
            } else {
                creees++;
            }
            if (!simulation) {
                ecrire(existant.orElseGet(Article::new), ligne, calcul, code, entreprise, retenue);
            }
        }

        RapportImportDto rapport =
                new RapportImportDto(simulation, lues, creees, modifiees, List.copyOf(refusees));
        log.info("Import du catalogue de l'entreprise {} : {}", entreprise, rapport);
        return rapport;
    }

    /** Ce qui empeche d'ecrire cette ligne, ou {@code null} si rien ne s'y oppose. */
    private String raisonDeRefus(Row ligne, FormulaEvaluator calcul, String code,
                                 Map<String, Integer> dejaVus, int numero) {
        if (code.isEmpty()) {
            return "Code absent : c'est lui qui identifie l'article et que lit la douchette";
        }
        Integer premiere = dejaVus.get(code);
        if (premiere != null) {
            return "Ce code apparaît déjà ligne " + premiere + " du fichier";
        }
        if (texte(ligne.getCell(DESIGNATION), calcul).isEmpty()) {
            return "Désignation absente";
        }

        BigDecimal prix = decimal(ligne.getCell(PRIX_HT), calcul);
        if (prix == null) {
            return "Prix HT absent ou illisible";
        }
        if (prix.signum() < 0) {
            return "Prix HT négatif";
        }

        BigDecimal taux = decimal(ligne.getCell(TAUX_TVA), calcul);
        if (taux != null && (taux.signum() < 0 || taux.compareTo(BigDecimal.valueOf(100)) > 0)) {
            return "Taux de TVA hors de 0 à 100";
        }

        BigDecimal seuil = decimal(ligne.getCell(SEUIL), calcul);
        if (seuil != null && seuil.signum() < 0) {
            return "Seuil d'alerte négatif";
        }
        // La cellule porte quelque chose qui n'est pas un nombre : mieux vaut le dire que de
        // l'ignorer en silence et rendre un seuil vide sans raison apparente.
        if (seuil == null && !texte(ligne.getCell(SEUIL), calcul).isEmpty()) {
            return "Seuil d'alerte illisible";
        }
        if (taux == null && !texte(ligne.getCell(TAUX_TVA), calcul).isEmpty()) {
            return "Taux de TVA illisible";
        }
        return null;
    }

    private void ecrire(Article article, Row ligne, FormulaEvaluator calcul,
                        String code, Long entreprise, UniteMesure unite) {
        BigDecimal prix = decimal(ligne.getCell(PRIX_HT), calcul);
        BigDecimal taux = decimal(ligne.getCell(TAUX_TVA), calcul);

        article.setCodeArticle(code);
        article.setDesignation(texte(ligne.getCell(DESIGNATION), calcul));
        article.setPrixUnitaire(prix);
        article.setTauxTva(taux);
        // Commodite d'affichage, recalculee a chaque import : la facture, elle, fige son propre
        // taux a l'emission et ne lit pas celui-ci.
        article.setPrixUnitTTC(taux == null ? null
                : prix.multiply(BigDecimal.ONE.add(taux.movePointLeft(2))).setScale(2, RoundingMode.HALF_UP));
        article.setSeuilAlerte(decimal(ligne.getCell(SEUIL), calcul));
        article.setIdEntreprise(entreprise);
        article.setUniteBase(unite);

        String categorie = texte(ligne.getCell(CATEGORIE), calcul);
        article.setCategory(categorie.isEmpty() ? null : categorie(categorie, entreprise));

        articleRepository.save(article);
    }

    /**
     * La categorie portant ce code, creee si elle n'existe pas.
     *
     * Exiger qu'elle existe d'abord obligerait le commercant a saisir ses categories a la main
     * avant de pouvoir importer — c'est-a-dire a buter sur le mur que cet import doit abattre.
     */
    private Category categorie(String code, Long entreprise) {
        return categoryRepository.findCategoriesByCodeCatAndIdEntreprise(code, entreprise)
                .orElseGet(() -> {
                    Category nouvelle = new Category();
                    nouvelle.setCodeCat(code);
                    nouvelle.setDesignation(code);
                    nouvelle.setIdEntreprise(entreprise);
                    return categoryRepository.save(nouvelle);
                });
    }

    /** « pièce », « kg », « Litres », « m² » : ce qu'on ecrit dans un tableur pour dire une unite. */
    private UniteMesure unite(String saisie) {
        String u = sansAccentNiCasse(saisie).replace("²", "2").replace("³", "3").replace(".", "");
        return switch (u) {
            case "piece", "pieces", "pc", "pcs", "u", "unite", "unites" -> UniteMesure.PIECE;
            case "kg", "kilo", "kilos", "kilogramme", "kilogrammes" -> UniteMesure.KG;
            case "l", "litre", "litres" -> UniteMesure.LITRE;
            case "m", "metre", "metres", "ml" -> UniteMesure.METRE;
            case "m2", "metre_carre", "metres_carres" -> UniteMesure.M2;
            case "m3", "metre_cube", "metres_cubes" -> UniteMesure.M3;
            default -> {
                try {
                    yield UniteMesure.valueOf(u.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException inconnue) {
                    yield null;
                }
            }
        };
    }

    // --- la feuille des conditionnements ------------------------------------------------------

    private Sheet feuilleConditionnements(Workbook classeur) {
        for (int i = 1; i < classeur.getNumberOfSheets(); i++) {
            if (FEUILLE_CONDITIONNEMENTS.equals(sansAccentNiCasse(classeur.getSheetName(i)))) {
                return classeur.getSheetAt(i);
            }
        }
        return null;
    }

    /**
     * Les conditionnements, une fois les articles ecrits : chacun se rattache a un article du
     * fichier ou du catalogue, par son code.
     *
     * Les regles sont celles de la fiche article, verifiees ici avant toute ecriture : une ligne
     * fausse est refusee et dite dans le rapport, sans annuler les autres — et sans annuler non
     * plus l'import des articles, qui est deja juste.
     *
     * Un conditionnement se reconnait a son libelle : reimporter le fichier corrige la contenance
     * ou le prix du « Carton de 24 » au lieu d'en creer un second.
     */
    private RapportImportDto parcourirConditionnements(Sheet feuille, FormulaEvaluator calcul, Long entreprise,
                                                       boolean simulation, Map<String, UniteMesure> importes,
                                                       RapportImportDto articles) {
        Row entete = feuille.getRow(feuille.getFirstRowNum());
        List<String> lues = new ArrayList<>();
        for (int colonne = 0; colonne < COLONNES_CONDITIONNEMENTS.size(); colonne++) {
            lues.add(entete == null ? "" : sansAccentNiCasse(texte(entete.getCell(colonne), calcul)));
        }
        if (!lues.equals(COLONNES_CONDITIONNEMENTS)) {
            throw new InvalidEntityException(
                    "Les colonnes de la feuille Conditionnements ne sont pas celles du modèle. Attendu : "
                            + String.join(", ", COLONNES_CONDITIONNEMENTS) + ". Lu : " + String.join(", ", lues),
                    ErrorCodes.IMPORT_NOT_VALID);
        }

        List<LigneRefuseeDto> refusees = new ArrayList<>(articles.refusees());
        Set<String> codesVus = new java.util.HashSet<>();
        Set<String> libellesVus = new java.util.HashSet<>();
        int ecrits = 0;
        for (int index = feuille.getFirstRowNum() + 1; index <= feuille.getLastRowNum(); index++) {
            Row ligne = feuille.getRow(index);
            if (videSur(ligne, calcul, COLONNES_CONDITIONNEMENTS.size())) {
                continue;
            }
            int numero = index + 1;
            String codeArticle = texte(ligne.getCell(0), calcul);
            String libelle = texte(ligne.getCell(1), calcul);
            BigDecimal contenance = decimal(ligne.getCell(2), calcul);
            BigDecimal prix = decimal(ligne.getCell(3), calcul);
            String codeBarres = texte(ligne.getCell(4), calcul).replaceAll("\\s", "");

            Optional<Article> article = articleRepository.findArticleByCodeArticleAndIdEntreprise(codeArticle, entreprise);
            UniteMesure unite = importes.containsKey(codeArticle) ? importes.get(codeArticle)
                    : article.map(Article::getUniteBase).orElse(null);

            String refus = null;
            if (codeArticle.isEmpty()) {
                refus = "Code de l'article absent";
            } else if (unite == null) {
                refus = "Aucun article ne porte le code " + codeArticle;
            } else if (libelle.isEmpty() || libelle.length() > 60) {
                refus = "Libellé absent ou de plus de 60 caractères";
            } else if (!libellesVus.add(codeArticle + "|" + libelle.toLowerCase(Locale.ROOT))) {
                refus = "Ce conditionnement apparaît deux fois pour l'article " + codeArticle;
            } else if (contenance == null || contenance.signum() <= 0) {
                refus = "Contenance absente ou nulle";
            } else if (contenance.compareTo(BigDecimal.ONE) == 0) {
                refus = "Un conditionnement contient plus d'une unité";
            } else if (!unite.isFractionnable() && contenance.stripTrailingZeros().scale() > 0) {
                refus = "L'article se compte à la pièce : la contenance est un nombre entier";
            } else if (prix == null && !texte(ligne.getCell(3), calcul).isEmpty()) {
                refus = "Prix HT illisible";
            } else if (prix != null && prix.signum() < 0) {
                refus = "Prix HT négatif";
            } else if (!codeBarres.isEmpty()) {
                refus = raisonDeRefusDuCode(codeBarres, codeArticle, entreprise, codesVus);
            }
            if (refus != null) {
                refusees.add(new LigneRefuseeDto(numero, codeArticle.isEmpty() ? null : codeArticle,
                        "Feuille Conditionnements : " + refus));
                continue;
            }
            ecrits++;
            if (!simulation) {
                // Les articles du fichier sont ecrits : celui-ci est donc en base, cree ou non par cet import.
                Article cible = article.orElseGet(() -> articleRepository
                        .findArticleByCodeArticleAndIdEntreprise(codeArticle, entreprise).orElseThrow());
                ecrireConditionnement(cible, libelle, contenance, prix, codeBarres);
            }
        }
        return new RapportImportDto(articles.simulation(), articles.lues(), articles.creees(), articles.modifiees(),
                List.copyOf(refusees), ecrits);
    }

    /** Le code-barres d'une ligne : bien forme, et libre dans le magasin comme dans le fichier. */
    private String raisonDeRefusDuCode(String code, String codeArticle, Long entreprise, Set<String> codesVus) {
        if (code.length() > 64) {
            return "Code-barres de plus de 64 caractères";
        }
        TypeCodeBarres type = CodeEan.deviner(code);
        if (type == TypeCodeBarres.CODE128 && code.chars().allMatch(Character::isDigit)
                && List.of(8, 12, 13, 14).contains(code.length())) {
            return "Le code-barres " + code + " a une clé de contrôle fausse";
        }
        if (!codesVus.add(code)) {
            return "Le code-barres " + code + " apparaît deux fois dans la feuille";
        }
        Optional<CodeBarres> pris = codeBarresRepository.findByCodeAndIdEntreprise(code, entreprise);
        if (pris.isPresent() && !codeArticle.equals(pris.get().getArticle().getCodeArticle())) {
            return "Le code-barres " + code + " est déjà celui de l'article " + pris.get().getArticle().getDesignation();
        }
        Optional<Article> homonyme = articleRepository.findArticleByCodeArticleAndIdEntreprise(code, entreprise);
        if (homonyme.isPresent() && !codeArticle.equals(homonyme.get().getCodeArticle())) {
            return "Le code-barres " + code + " est le code de l'article " + homonyme.get().getDesignation();
        }
        return null;
    }

    private void ecrireConditionnement(Article article, String libelle, BigDecimal contenance, BigDecimal prix,
                                       String codeBarres) {
        Conditionnement conditionnement = conditionnementRepository
                .findAllByArticleIdOrderByQuantiteUnitesAsc(article.getId()).stream()
                .filter(Conditionnement::isActif)
                .filter(c -> c.getLibelle().equalsIgnoreCase(libelle))
                .findFirst()
                .orElseGet(() -> {
                    Conditionnement neuf = new Conditionnement();
                    neuf.setArticle(article);
                    neuf.setIdEntreprise(article.getIdEntreprise());
                    neuf.setLibelle(libelle);
                    return neuf;
                });
        conditionnement.setQuantiteUnites(contenance);
        conditionnement.setPrixVenteHt(prix);
        conditionnement.setVendable(prix != null);
        conditionnement.setAchetable(true);
        Conditionnement enregistre = conditionnementRepository.save(conditionnement);

        if (!codeBarres.isEmpty() && codeBarresRepository
                .findByCodeAndIdEntreprise(codeBarres, article.getIdEntreprise()).isEmpty()) {
            CodeBarres code = new CodeBarres();
            code.setArticle(article);
            code.setConditionnement(enregistre);
            code.setIdEntreprise(article.getIdEntreprise());
            code.setCode(codeBarres);
            code.setType(CodeEan.deviner(codeBarres));
            codeBarresRepository.save(code);
        }
    }

    // --- lecture des cellules -----------------------------------------------------------------

    /**
     * Le contenu d'une cellule, en texte.
     *
     * Le piege est le code-barres : saisi dans un tableur, « 3017620422003 » devient un nombre,
     * et une lecture naive rend « 3.01762E+12 ». Un code ainsi tronque ne se scanne plus, et rien
     * a l'ecran ne dit pourquoi. Les entiers sont donc rendus sans notation scientifique.
     */
    private String texte(Cell cellule, FormulaEvaluator calcul) {
        if (cellule == null) {
            return "";
        }
        CellType type = cellule.getCellType() == CellType.FORMULA
                ? calcul.evaluateFormulaCell(cellule)
                : cellule.getCellType();
        return switch (type) {
            case STRING -> cellule.getStringCellValue().trim();
            case NUMERIC -> nombreEnTexte(cellule.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cellule.getBooleanCellValue());
            default -> "";
        };
    }

    private String nombreEnTexte(double valeur) {
        BigDecimal exact = BigDecimal.valueOf(valeur);
        return exact.stripTrailingZeros().scale() <= 0
                ? exact.toBigInteger().toString()
                : exact.stripTrailingZeros().toPlainString();
    }

    /**
     * Un nombre, qu'il ait ete saisi comme tel ou comme texte.
     *
     * « 1 500,50 » est ce que tape quelqu'un qui compte en francais, et son tableur le laisse
     * parfois en texte. Le refuser reviendrait a refuser la moitie des fichiers pour une virgule.
     */
    private BigDecimal decimal(Cell cellule, FormulaEvaluator calcul) {
        if (cellule == null) {
            return null;
        }
        CellType type = cellule.getCellType() == CellType.FORMULA
                ? calcul.evaluateFormulaCell(cellule)
                : cellule.getCellType();
        if (type == CellType.NUMERIC) {
            return BigDecimal.valueOf(cellule.getNumericCellValue());
        }
        String brut = texte(cellule, calcul)
                .replace(" ", "")
                .replace(" ", "")
                .replace(',', '.');
        if (brut.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(brut);
        } catch (NumberFormatException illisible) {
            return null;
        }
    }

    private boolean vide(Row ligne, FormulaEvaluator calcul) {
        return videSur(ligne, calcul, COLONNES.size());
    }

    private boolean videSur(Row ligne, FormulaEvaluator calcul, int colonnes) {
        if (ligne == null) {
            return true;
        }
        for (int colonne = 0; colonne < colonnes; colonne++) {
            if (!texte(ligne.getCell(colonne), calcul).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** « Prix_HT », « prix ht » et « PRIX_HT » designent la meme colonne. */
    private String sansAccentNiCasse(String valeur) {
        String sansAccent = Normalizer.normalize(valeur, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sansAccent.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
    }

    // --- le modele ----------------------------------------------------------------------------

    @Override
    public byte[] modele() {
        try (Workbook classeur = new XSSFWorkbook();
             ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {

            Sheet feuille = classeur.createSheet("Articles");
            CreationHelper aide = classeur.getCreationHelper();

            Row entete = feuille.createRow(0);
            for (int colonne = 0; colonne < COLONNES.size(); colonne++) {
                entete.createCell(colonne).setCellValue(aide.createRichTextString(COLONNES.get(colonne)));
            }
            entete.createCell(UNITE).setCellValue(aide.createRichTextString(COLONNE_UNITE));

            // Une ligne d'exemple plutot qu'une notice : elle montre le format du code-barres, la
            // virgule decimale et qu'une colonne peut rester vide.
            Row exemple = feuille.createRow(1);
            exemple.createCell(CODE).setCellValue("3017620422003");
            exemple.createCell(DESIGNATION).setCellValue("Sac de ciment 50 kg");
            exemple.createCell(PRIX_HT).setCellValue(4650);
            exemple.createCell(TAUX_TVA).setCellValue(19.25);
            exemple.createCell(SEUIL).setCellValue(10);
            exemple.createCell(CATEGORIE).setCellValue("CIMENT");
            exemple.createCell(UNITE).setCellValue("pièce");

            for (int colonne = 0; colonne <= UNITE; colonne++) {
                feuille.autoSizeColumn(colonne);
            }

            // La vente en gros : la palette du meme sac, son prix et le code imprime dessus.
            Sheet conditionnements = classeur.createSheet("Conditionnements");
            Row enteteC = conditionnements.createRow(0);
            for (int colonne = 0; colonne < COLONNES_CONDITIONNEMENTS.size(); colonne++) {
                enteteC.createCell(colonne).setCellValue(aide.createRichTextString(COLONNES_CONDITIONNEMENTS.get(colonne)));
            }
            Row exempleC = conditionnements.createRow(1);
            exempleC.createCell(0).setCellValue("3017620422003");
            exempleC.createCell(1).setCellValue("Palette de 40 sacs");
            exempleC.createCell(2).setCellValue(40);
            exempleC.createCell(3).setCellValue(180000);
            for (int colonne = 0; colonne < COLONNES_CONDITIONNEMENTS.size(); colonne++) {
                conditionnements.autoSizeColumn(colonne);
            }

            classeur.write(sortie);
            return sortie.toByteArray();
        } catch (IOException echec) {
            throw new InvalidEntityException("Le modèle n'a pas pu être produit",
                    ErrorCodes.IMPORT_NOT_VALID);
        }
    }
}
