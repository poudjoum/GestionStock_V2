package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.RapportImportDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.RapportImportDto.LigneRefuseeDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Adresse;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Client;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Fournisseur;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ClientRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.FournisseurRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.ImportRepertoireService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
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
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Voir {@link ImportRepertoireService}. */
@Service
@Slf4j
public class ImportRepertoireServiceImpl implements ImportRepertoireService {

    /**
     * Les colonnes du modele, dans l'ordre : les memes pour les clients et pour les fournisseurs.
     *
     * Le prenom vide fait une entreprise — « BTP Wouri SARL » —, rempli un particulier : c'est la
     * regle des fiches saisies a l'ecran, et le classeur n'a pas besoin d'une colonne de plus pour
     * le dire.
     */
    private static final List<String> COLONNES =
            List.of("nom", "prenom", "telephone", "courriel", "adresse", "ville", "pays");

    private static final int NOM = 0;
    private static final int PRENOM = 1;
    private static final int TELEPHONE = 2;
    private static final int COURRIEL = 3;
    private static final int ADRESSE = 4;
    private static final int VILLE = 5;
    private static final int PAYS = 6;

    private static final int LIGNES_MAX = 10_000;

    /** Assez pour refuser une faute de frappe, pas assez pour refuser une adresse inhabituelle. */
    private static final Pattern COURRIEL_VALIDE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ClientRepository clientRepository;
    private final FournisseurRepository fournisseurRepository;
    private final Cloisonnement cloisonnement;

    public ImportRepertoireServiceImpl(ClientRepository clientRepository,
                                       FournisseurRepository fournisseurRepository,
                                       Cloisonnement cloisonnement) {
        this.clientRepository = clientRepository;
        this.fournisseurRepository = fournisseurRepository;
        this.cloisonnement = cloisonnement;
    }

    @Override
    @Transactional
    public RapportImportDto importer(Repertoire repertoire, MultipartFile fichier, boolean simulation) {
        Long entreprise = cloisonnement.entrepriseCourante();
        if (entreprise == null) {
            throw new InvalidEntityException(
                    "Ce compte n'est rattaché à aucune entreprise : il n'y a pas de répertoire où importer",
                    ErrorCodes.IMPORT_NOT_VALID);
        }
        if (fichier == null || fichier.isEmpty()) {
            throw new InvalidEntityException("Aucun fichier n'a été reçu", ErrorCodes.IMPORT_NOT_VALID);
        }

        try (InputStream flux = fichier.getInputStream();
             Workbook classeur = WorkbookFactory.create(flux)) {

            Sheet feuille = classeur.getSheetAt(0);
            FormulaEvaluator calcul = classeur.getCreationHelper().createFormulaEvaluator();
            verifierEntete(feuille, calcul);
            return parcourir(repertoire, feuille, calcul, entreprise, simulation);

        } catch (IOException | RuntimeException echec) {
            if (echec instanceof InvalidEntityException connue) {
                throw connue;
            }
            log.warn("Classeur illisible a l'import du repertoire : {}", echec.getMessage());
            throw new InvalidEntityException(
                    "Ce fichier n'a pas pu être lu. Attendu : un classeur Excel (.xlsx) au format du modèle.",
                    ErrorCodes.IMPORT_NOT_VALID);
        }
    }

    private void verifierEntete(Sheet feuille, FormulaEvaluator calcul) {
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
    }

    private RapportImportDto parcourir(Repertoire repertoire, Sheet feuille, FormulaEvaluator calcul,
                                       Long entreprise, boolean simulation) {
        // Les fiches deja en base, par telephone : une seule lecture, plutot qu'une requete par
        // ligne d'un fichier qui peut en compter des milliers.
        Map<String, Object> existantes = new HashMap<>();
        if (repertoire == Repertoire.CLIENTS) {
            for (Client c : clientRepository.findAllByIdEntreprise(entreprise)) {
                existantes.putIfAbsent(telephoneNormalise(c.getNumTel()), c);
            }
        } else {
            for (Fournisseur f : fournisseurRepository.findAllByIdEntreprise(entreprise)) {
                existantes.putIfAbsent(telephoneNormalise(f.getTel()), f);
            }
        }

        List<LigneRefuseeDto> refusees = new ArrayList<>();
        Map<String, Integer> dejaVus = new HashMap<>();
        int lues = 0;
        int creees = 0;
        int modifiees = 0;

        for (int index = feuille.getFirstRowNum() + 1; index <= feuille.getLastRowNum(); index++) {
            Row ligne = feuille.getRow(index);
            if (vide(ligne, calcul)) {
                continue;
            }
            int numero = index + 1;
            lues++;
            if (lues > LIGNES_MAX) {
                throw new InvalidEntityException(
                        "Le fichier dépasse " + LIGNES_MAX + " lignes", ErrorCodes.IMPORT_NOT_VALID);
            }

            String nom = texte(ligne.getCell(NOM), calcul);
            String telephone = telephoneNormalise(texte(ligne.getCell(TELEPHONE), calcul));
            String courriel = texte(ligne.getCell(COURRIEL), calcul);

            String refus = null;
            if (nom.isEmpty()) {
                refus = "Nom absent";
            } else if (telephone.isEmpty()) {
                refus = "Téléphone absent : c'est lui qui reconnaît la fiche";
            } else if (telephone.length() < 8) {
                refus = "Téléphone trop court : au moins 8 chiffres";
            } else if (dejaVus.containsKey(telephone)) {
                refus = "Ce téléphone apparaît déjà ligne " + dejaVus.get(telephone) + " du fichier";
            } else if (courriel.isEmpty()) {
                // Exige a l'ecran comme ici : une fiche importee sans courriel ne pourrait plus etre
                // enregistree le jour ou on la corrige.
                refus = "Courriel absent";
            } else if (!COURRIEL_VALIDE.matcher(courriel).matches()) {
                refus = "Courriel illisible : « " + courriel + " »";
            }
            if (refus != null) {
                refusees.add(new LigneRefuseeDto(numero, nom.isEmpty() ? null : nom, refus));
                continue;
            }
            dejaVus.put(telephone, numero);

            Object existante = existantes.get(telephone);
            if (existante != null) {
                modifiees++;
            } else {
                creees++;
            }
            if (!simulation) {
                ecrire(repertoire, existante, ligne, calcul, entreprise);
            }
        }

        RapportImportDto rapport =
                new RapportImportDto(simulation, lues, creees, modifiees, List.copyOf(refusees));
        log.info("Import des {} de l'entreprise {} : {}", repertoire.pluriel, entreprise, rapport);
        return rapport;
    }

    private void ecrire(Repertoire repertoire, Object existante, Row ligne, FormulaEvaluator calcul,
                        Long entreprise) {
        String nom = texte(ligne.getCell(NOM), calcul);
        String prenom = texte(ligne.getCell(PRENOM), calcul);
        String telephone = texte(ligne.getCell(TELEPHONE), calcul);
        String courriel = texte(ligne.getCell(COURRIEL), calcul);
        Adresse adresse = adresse(ligne, calcul);

        if (repertoire == Repertoire.CLIENTS) {
            Client client = existante instanceof Client c ? c : new Client();
            client.setNom(nom);
            client.setPrenoms(prenom.isEmpty() ? null : prenom);
            client.setNumTel(telephone);
            client.setMail(courriel);
            if (adresse != null) {
                client.setAdresse(adresse);
            }
            client.setIdEntreprise(entreprise);
            clientRepository.save(client);
        } else {
            Fournisseur fournisseur = existante instanceof Fournisseur f ? f : new Fournisseur();
            fournisseur.setNom(nom);
            fournisseur.setPrenom(prenom.isEmpty() ? null : prenom);
            fournisseur.setTel(telephone);
            fournisseur.setMail(courriel);
            if (adresse != null) {
                fournisseur.setAdresse(adresse);
            }
            fournisseur.setIdEntreprise(entreprise);
            fournisseurRepository.save(fournisseur);
        }
    }

    /** L'adresse, si le fichier en porte une : une colonne vide ne doit pas effacer celle qu'on a. */
    private Adresse adresse(Row ligne, FormulaEvaluator calcul) {
        String rue = texte(ligne.getCell(ADRESSE), calcul);
        String ville = texte(ligne.getCell(VILLE), calcul);
        String pays = texte(ligne.getCell(PAYS), calcul);
        if (rue.isEmpty() && ville.isEmpty() && pays.isEmpty()) {
            return null;
        }
        Adresse adresse = new Adresse();
        adresse.setAdresse1(rue.isEmpty() ? null : rue);
        adresse.setVille(ville.isEmpty() ? null : ville);
        adresse.setPays(pays.isEmpty() ? null : pays);
        return adresse;
    }

    /**
     * Le telephone reduit a ses chiffres : « 6 90 12 34 56 », « 690-12-34-56 » et « 690123456 »
     * sont le meme numero. L'indicatif +237 est retire, pour qu'un fichier qui le porte reconnaisse
     * les fiches saisies sans lui.
     */
    static String telephoneNormalise(String brut) {
        if (brut == null) {
            return "";
        }
        String chiffres = brut.replaceAll("\\D", "");
        if (chiffres.startsWith("237") && chiffres.length() == 12) {
            chiffres = chiffres.substring(3);
        }
        return chiffres;
    }

    // --- lecture des cellules : les memes regles que l'import du catalogue --------------------

    private String texte(Cell cellule, FormulaEvaluator calcul) {
        if (cellule == null) {
            return "";
        }
        CellType type = cellule.getCellType() == CellType.FORMULA
                ? calcul.evaluateFormulaCell(cellule)
                : cellule.getCellType();
        return switch (type) {
            case STRING -> cellule.getStringCellValue().trim();
            // Un telephone saisi comme nombre : sans notation scientifique ni « .0 ».
            case NUMERIC -> {
                BigDecimal exact = BigDecimal.valueOf(cellule.getNumericCellValue()).stripTrailingZeros();
                yield exact.scale() <= 0 ? exact.toBigInteger().toString() : exact.toPlainString();
            }
            case BOOLEAN -> String.valueOf(cellule.getBooleanCellValue());
            default -> "";
        };
    }

    private boolean vide(Row ligne, FormulaEvaluator calcul) {
        if (ligne == null) {
            return true;
        }
        for (int colonne = 0; colonne < COLONNES.size(); colonne++) {
            if (!texte(ligne.getCell(colonne), calcul).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String sansAccentNiCasse(String valeur) {
        String sansAccent = Normalizer.normalize(valeur, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sansAccent.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
    }

    // --- le modele ----------------------------------------------------------------------------

    @Override
    public byte[] modele(Repertoire repertoire) {
        try (Workbook classeur = new XSSFWorkbook();
             ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {

            Sheet feuille = classeur.createSheet(repertoire == Repertoire.CLIENTS ? "Clients" : "Fournisseurs");
            Row entete = feuille.createRow(0);
            for (int colonne = 0; colonne < COLONNES.size(); colonne++) {
                entete.createCell(colonne).setCellValue(COLONNES.get(colonne));
            }

            // Deux exemples : une personne, et une entreprise — dont le prenom reste vide.
            String[][] exemples = repertoire == Repertoire.CLIENTS
                    ? new String[][]{
                        {"Ngo Bassa", "Aline", "677 11 22 33", "aline.ngobassa@exemple.cm", "Rue Joss", "Douala", "Cameroun"},
                        {"BTP Wouri SARL", "", "233 40 10 20", "achats@btpwouri.cm", "Zone industrielle Bassa", "Douala", "Cameroun"}}
                    : new String[][]{
                        {"Cimencam", "", "233 50 50 50", "commandes@cimencam.cm", "Bonabéri", "Douala", "Cameroun"},
                        {"Tchoupo", "Martin", "699 88 77 66", "martin.tchoupo@exemple.cm", "Marché Mboppi", "Douala", "Cameroun"}};
            for (int i = 0; i < exemples.length; i++) {
                Row ligne = feuille.createRow(i + 1);
                for (int colonne = 0; colonne < COLONNES.size(); colonne++) {
                    ligne.createCell(colonne).setCellValue(exemples[i][colonne]);
                }
            }
            for (int colonne = 0; colonne < COLONNES.size(); colonne++) {
                feuille.autoSizeColumn(colonne);
            }

            classeur.write(sortie);
            return sortie.toByteArray();
        } catch (IOException echec) {
            throw new InvalidEntityException("Le modèle n'a pas pu être produit", ErrorCodes.IMPORT_NOT_VALID);
        }
    }
}
