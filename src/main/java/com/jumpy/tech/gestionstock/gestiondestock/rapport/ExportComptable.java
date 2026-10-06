package com.jumpy.tech.gestionstock.gestiondestock.rapport;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Entreprise;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Le classeur du comptable : trois onglets qu'il peut passer en ecritures sans les retoucher.
 *
 * Ce qui en fait un document de travail et non une copie d'ecran : des dates et des montants
 * types — triables, filtrables, additionnables —, des totaux en formules qu'il peut verifier,
 * l'en-tete du commerce et la periode sur chaque onglet, des titres de colonnes figes et filtres,
 * et une mise en page qui sort propre a l'impression.
 */
@Component
public class ExportComptable {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE);
    /** Le vert de la marque, pour les titres de colonnes. */
    private static final byte[] VERT = {(byte) 0x1d, (byte) 0x6b, (byte) 0x43};
    private static final byte[] VERT_PALE = {(byte) 0xdd, (byte) 0xef, (byte) 0xe3};
    private static final byte[] GRIS_TRAIT = {(byte) 0xd9, (byte) 0xdf, (byte) 0xd9};
    private static final byte[] GRIS_TEXTE = {(byte) 0x80, (byte) 0x8a, (byte) 0x84};

    /** Le classeur, en octets. */
    public byte[] classeur(RapportComptableDto rapport, Entreprise entreprise, ZoneId fuseau) {
        try (XSSFWorkbook classeur = new XSSFWorkbook(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            Styles styles = new Styles(classeur);
            String commerce = entreprise == null || entreprise.getNom() == null ? "GestionStock" : entreprise.getNom();
            Entete entete = new Entete(commerce, entreprise == null ? null : entreprise.getNiu(),
                    "Du " + JOUR.format(rapport.getDebut()) + " au " + JOUR.format(rapport.getFin()),
                    rapport.getNomSite() == null ? "Tous les sites" : rapport.getNomSite(),
                    "Édité le " + HORODATAGE.format(LocalDateTime.now(fuseau)));

            synthese(classeur, styles, entete, rapport);
            journalVentes(classeur, styles, entete, rapport, fuseau);
            encaissements(classeur, styles, entete, rapport, fuseau);

            classeur.getProperties().getCoreProperties().setTitle("Comptabilité " + commerce + " — " + entete.periode());
            classeur.getProperties().getCoreProperties().setCreator("GestionStock");
            classeur.setActiveSheet(0);
            classeur.write(sortie);
            return sortie.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Le classeur n'a pas pu être écrit", e);
        }
    }

    /** « Comptabilite_Superette-Akwa_2026-09.xlsx » : le commerce et la periode, sans espace ni accent. */
    public static String nomDeFichier(Entreprise entreprise, RapportComptableDto rapport) {
        String commerce = entreprise == null || entreprise.getNom() == null ? "commerce" : entreprise.getNom();
        String propre = java.text.Normalizer.normalize(commerce, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^A-Za-z0-9]+", "-").replaceAll("(^-|-$)", "");
        boolean moisEntier = rapport.getDebut().getDayOfMonth() == 1
                && rapport.getFin().equals(rapport.getDebut().withDayOfMonth(rapport.getDebut().lengthOfMonth()));
        String periode = moisEntier
                ? rapport.getDebut().toString().substring(0, 7)
                : rapport.getDebut() + "_" + rapport.getFin();
        return "Comptabilite_" + propre + "_" + periode + ".xlsx";
    }

    // --- Les onglets -------------------------------------------------------------------------

    private void synthese(XSSFWorkbook classeur, Styles s, Entete entete, RapportComptableDto r) {
        XSSFSheet feuille = classeur.createSheet("Synthèse");
        int ligne = entete.ecrire(feuille, s, "Synthèse de la période", 4);

        ligne = titreDeBloc(feuille, s, ligne, "Chiffres de la période");
        ligne = cle(feuille, s, ligne, "Chiffre d’affaires HT", r.getTotalHt());
        ligne = cle(feuille, s, ligne, "TVA collectée", r.getTotalTva());
        ligne = cle(feuille, s, ligne, "Chiffre d’affaires TTC", r.getTotalTtc());
        ligne = cle(feuille, s, ligne, "Encaissé sur la période", r.getTotalEncaisse());
        ligne = cleNombre(feuille, s, ligne, "Factures émises", r.getFactures());
        ligne = cleNombre(feuille, s, ligne, "Factures annulées", r.getFacturesAnnulees());
        ligne++;

        ligne = titreDeBloc(feuille, s, ligne, "TVA collectée par taux");
        int premiere = titres(feuille, s, ligne, "Taux", "Base HT (FCFA)", "TVA (FCFA)", "TTC (FCFA)", "Factures");
        int l = premiere;
        for (RapportComptableDto.LigneTva t : r.getTva()) {
            Row row = feuille.createRow(l++);
            texte(row, 0, t.getLibelle(), s.texte);
            montant(row, 1, t.getBaseHt(), s.montant);
            montant(row, 2, t.getTva(), s.montant);
            montant(row, 3, t.getTtc(), s.montant);
            entier(row, 4, t.getFactures(), s.entier);
        }
        if (r.getTva().isEmpty()) {
            l = vide(feuille, s, l, "Aucune facture émise sur la période.", 5);
        } else {
            l = totaux(feuille, s, l, premiere, new int[]{1, 2, 3}, 5);
        }
        ligne = l + 1;

        ligne = titreDeBloc(feuille, s, ligne, "Encaissements par mode de règlement");
        premiere = titres(feuille, s, ligne, "Mode", "Montant (FCFA)", "Règlements");
        l = premiere;
        for (RapportComptableDto.EncaissementParMode m : r.getEncaissementsParMode()) {
            Row row = feuille.createRow(l++);
            texte(row, 0, m.getLibelle(), s.texte);
            montant(row, 1, m.getMontant(), s.montant);
            entier(row, 2, m.getNombre(), s.entier);
        }
        if (r.getEncaissementsParMode().isEmpty()) {
            vide(feuille, s, l, "Aucun règlement reçu sur la période.", 3);
        } else {
            totaux(feuille, s, l, premiere, new int[]{1}, 3);
        }

        largeurs(feuille, 34, 20, 18, 20, 12);
        impression(feuille, entete, false);
    }

    private void journalVentes(XSSFWorkbook classeur, Styles s, Entete entete, RapportComptableDto r, ZoneId fuseau) {
        XSSFSheet feuille = classeur.createSheet("Journal des ventes");
        int ligne = entete.ecrire(feuille, s, "Journal des ventes — une ligne par facture émise", 9);
        String[] colonnes = {"Date", "N° de facture", "Client", "Total HT (FCFA)", "TVA (FCFA)", "Total TTC (FCFA)",
                "Réglé (FCFA)", "Reste dû (FCFA)", "Statut"};
        int premiere = titres(feuille, s, ligne, colonnes);
        int l = premiere;
        for (RapportComptableDto.LigneJournalVentes v : r.getJournalVentes()) {
            Row row = feuille.createRow(l++);
            boolean annulee = v.isAnnulee();
            date(row, 0, v.getDate(), fuseau, annulee ? s.dateAnnulee : s.date);
            texte(row, 1, v.getNumero(), annulee ? s.texteAnnule : s.texte);
            texte(row, 2, v.getClient(), annulee ? s.texteAnnule : s.texte);
            montant(row, 3, v.getTotalHt(), annulee ? s.montantAnnule : s.montant);
            montant(row, 4, v.getTotalTva(), annulee ? s.montantAnnule : s.montant);
            montant(row, 5, v.getTotalTtc(), annulee ? s.montantAnnule : s.montant);
            montant(row, 6, v.getRegle(), annulee ? s.montantAnnule : s.montant);
            montant(row, 7, v.getReste(), annulee ? s.montantAnnule : s.montant);
            texte(row, 8, annulee ? "Annulée"
                    : v.getReste().signum() == 0 ? "Réglée"
                    : v.getRegle().signum() == 0 ? "Impayée" : "Partielle", annulee ? s.texteAnnule : s.texte);
        }
        if (r.getJournalVentes().isEmpty()) {
            vide(feuille, s, l, "Aucune facture émise sur la période.", colonnes.length);
        } else {
            filtre(feuille, premiere - 1, l - 1, colonnes.length);
            totaux(feuille, s, l, premiere, new int[]{3, 4, 5, 6, 7}, colonnes.length);
        }
        feuille.createFreezePane(0, premiere);
        largeurs(feuille, 18, 18, 30, 17, 15, 17, 15, 15, 11);
        impression(feuille, entete, true);
        feuille.setRepeatingRows(new CellRangeAddress(premiere - 1, premiere - 1, 0, colonnes.length - 1));
    }

    private void encaissements(XSSFWorkbook classeur, Styles s, Entete entete, RapportComptableDto r, ZoneId fuseau) {
        XSSFSheet feuille = classeur.createSheet("Encaissements");
        int ligne = entete.ecrire(feuille, s, "Journal des encaissements — une ligne par règlement reçu", 6);
        String[] colonnes = {"Date", "N° de facture", "Client", "Mode", "Montant (FCFA)", "Référence"};
        int premiere = titres(feuille, s, ligne, colonnes);
        int l = premiere;
        for (RapportComptableDto.LigneJournalEncaissements e : r.getJournalEncaissements()) {
            Row row = feuille.createRow(l++);
            date(row, 0, e.getDate(), fuseau, s.date);
            texte(row, 1, e.getNumeroFacture(), s.texte);
            texte(row, 2, e.getClient(), s.texte);
            texte(row, 3, RapportComptableService.MODES.getOrDefault(e.getMode(), e.getMode()), s.texte);
            montant(row, 4, e.getMontant(), s.montant);
            texte(row, 5, e.getReference(), s.texte);
        }
        if (r.getJournalEncaissements().isEmpty()) {
            vide(feuille, s, l, "Aucun règlement reçu sur la période.", colonnes.length);
        } else {
            filtre(feuille, premiere - 1, l - 1, colonnes.length);
            totaux(feuille, s, l, premiere, new int[]{4}, colonnes.length);
        }
        feuille.createFreezePane(0, premiere);
        largeurs(feuille, 18, 18, 30, 16, 17, 26);
        impression(feuille, entete, true);
        feuille.setRepeatingRows(new CellRangeAddress(premiere - 1, premiere - 1, 0, colonnes.length - 1));
    }

    // --- Les briques -------------------------------------------------------------------------

    /** L'en-tete de chaque onglet : le commerce, la periode, le site, la date d'edition. */
    private record Entete(String commerce, String niu, String periode, String site, String edition) {

        int ecrire(XSSFSheet feuille, Styles s, String titre, int colonnes) {
            Row r0 = feuille.createRow(0);
            r0.setHeightInPoints(24);
            texte(r0, 0, commerce, s.commerce);
            Row r1 = feuille.createRow(1);
            texte(r1, 0, titre, s.sousTitre);
            Row r2 = feuille.createRow(2);
            texte(r2, 0, periode + "  ·  " + site + (niu == null || niu.isBlank() ? "" : "  ·  NIU " + niu)
                    + "  ·  " + edition, s.discret);
            for (int i = 0; i < 3; i++) {
                feuille.addMergedRegion(new CellRangeAddress(i, i, 0, Math.max(colonnes - 1, 1)));
            }
            return 4;
        }
    }

    private static int titreDeBloc(XSSFSheet feuille, Styles s, int ligne, String titre) {
        texte(feuille.createRow(ligne), 0, titre, s.bloc);
        return ligne + 1;
    }

    private static int cle(XSSFSheet feuille, Styles s, int ligne, String libelle, BigDecimal valeur) {
        Row row = feuille.createRow(ligne);
        texte(row, 0, libelle, s.texte);
        montant(row, 1, valeur, s.montantFort);
        return ligne + 1;
    }

    private static int cleNombre(XSSFSheet feuille, Styles s, int ligne, String libelle, long valeur) {
        Row row = feuille.createRow(ligne);
        texte(row, 0, libelle, s.texte);
        entier(row, 1, valeur, s.entier);
        return ligne + 1;
    }

    /** La ligne des titres de colonnes ; rend la premiere ligne de donnees. */
    private static int titres(XSSFSheet feuille, Styles s, int ligne, String... titres) {
        Row row = feuille.createRow(ligne);
        row.setHeightInPoints(20);
        for (int i = 0; i < titres.length; i++) {
            texte(row, i, titres[i], i == 0 || !titres[i].contains("(FCFA)") && !titres[i].equals("Factures")
                    && !titres[i].equals("Règlements") ? s.titre : s.titreDroite);
        }
        return ligne + 1;
    }

    /** Les totaux en formules : le comptable peut les verifier, et ils suivent un filtre retire. */
    private static int totaux(XSSFSheet feuille, Styles s, int ligne, int premiere, int[] colonnes, int largeur) {
        Row row = feuille.createRow(ligne);
        for (int c = 0; c < largeur; c++) {
            row.createCell(c).setCellStyle(s.total);
        }
        row.getCell(0).setCellValue("Total");
        for (int c : colonnes) {
            String col = CellReference.convertNumToColString(c);
            Cell cell = row.getCell(c);
            cell.setCellFormula("SUM(" + col + (premiere + 1) + ":" + col + ligne + ")");
            cell.setCellStyle(s.totalMontant);
        }
        return ligne + 1;
    }

    private static int vide(XSSFSheet feuille, Styles s, int ligne, String message, int largeur) {
        texte(feuille.createRow(ligne), 0, message, s.discret);
        if (largeur > 1) {
            feuille.addMergedRegion(new CellRangeAddress(ligne, ligne, 0, largeur - 1));
        }
        return ligne + 1;
    }

    private static void filtre(XSSFSheet feuille, int ligneTitres, int derniere, int colonnes) {
        feuille.setAutoFilter(new CellRangeAddress(ligneTitres, derniere, 0, colonnes - 1));
    }

    private static void largeurs(XSSFSheet feuille, int... caracteres) {
        for (int i = 0; i < caracteres.length; i++) {
            feuille.setColumnWidth(i, caracteres[i] * 256 + 200);
        }
    }

    /** Paysage, ajuste a la largeur d'une page, le commerce en en-tete et « Page 2 / 5 » en pied. */
    private static void impression(XSSFSheet feuille, Entete entete, boolean paysage) {
        PrintSetup mise = feuille.getPrintSetup();
        mise.setPaperSize(PrintSetup.A4_PAPERSIZE);
        mise.setLandscape(paysage);
        mise.setFitWidth((short) 1);
        mise.setFitHeight((short) 0);
        feuille.setFitToPage(true);
        feuille.setHorizontallyCenter(true);
        feuille.getHeader().setLeft(entete.commerce());
        feuille.getHeader().setRight(entete.periode());
        feuille.getFooter().setLeft(entete.edition());
        feuille.getFooter().setRight("Page &P / &N");
        feuille.setMargin(org.apache.poi.ss.usermodel.Sheet.TopMargin, 0.6);
        feuille.setMargin(org.apache.poi.ss.usermodel.Sheet.BottomMargin, 0.6);
    }

    private static void texte(Row row, int c, String valeur, CellStyle style) {
        Cell cell = row.createCell(c);
        cell.setCellValue(valeur == null ? "" : valeur);
        cell.setCellStyle(style);
    }

    private static void montant(Row row, int c, BigDecimal valeur, CellStyle style) {
        Cell cell = row.createCell(c);
        cell.setCellValue(valeur == null ? 0 : valeur.doubleValue());
        cell.setCellStyle(style);
    }

    private static void entier(Row row, int c, long valeur, CellStyle style) {
        Cell cell = row.createCell(c);
        cell.setCellValue(valeur);
        cell.setCellStyle(style);
    }

    /** Une vraie date Excel, a l'heure du magasin : triable et filtrable par mois. */
    private static void date(Row row, int c, Instant instant, ZoneId fuseau, CellStyle style) {
        Cell cell = row.createCell(c);
        if (instant != null) {
            cell.setCellValue(LocalDateTime.ofInstant(instant, fuseau));
        }
        cell.setCellStyle(style);
    }

    /** Les styles, crees une fois : un classeur en a un nombre limite. */
    private static final class Styles {
        final CellStyle commerce, sousTitre, discret, bloc, titre, titreDroite, texte, texteAnnule, date, dateAnnulee,
                montant, montantAnnule, montantFort, entier, total, totalMontant;

        Styles(XSSFWorkbook c) {
            String formatMontant = "#,##0.00;[Red]-#,##0.00";
            short fMontant = c.createDataFormat().getFormat(formatMontant);
            short fDate = c.createDataFormat().getFormat("dd/mm/yyyy hh:mm");
            short fEntier = c.createDataFormat().getFormat("#,##0");

            commerce = style(c, police(c, 16, true, null), null);
            sousTitre = style(c, police(c, 12, true, null), null);
            discret = style(c, police(c, 9, false, GRIS_TEXTE), null);
            bloc = style(c, police(c, 11, true, VERT), null);

            titre = style(c, police(c, 10, true, new byte[]{(byte) 255, (byte) 255, (byte) 255}), VERT);
            titre.setVerticalAlignment(VerticalAlignment.CENTER);
            titreDroite = c.createCellStyle();
            titreDroite.cloneStyleFrom(titre);
            titreDroite.setAlignment(HorizontalAlignment.RIGHT);

            texte = trait(c, style(c, police(c, 10, false, null), null));
            texteAnnule = trait(c, style(c, police(c, 10, false, GRIS_TEXTE, true), null));
            date = trait(c, style(c, police(c, 10, false, null), null));
            date.setDataFormat(fDate);
            date.setAlignment(HorizontalAlignment.LEFT);
            dateAnnulee = trait(c, style(c, police(c, 10, false, GRIS_TEXTE, true), null));
            dateAnnulee.setDataFormat(fDate);
            dateAnnulee.setAlignment(HorizontalAlignment.LEFT);
            montant = trait(c, style(c, police(c, 10, false, null), null));
            montant.setDataFormat(fMontant);
            montantAnnule = trait(c, style(c, police(c, 10, false, GRIS_TEXTE, true), null));
            montantAnnule.setDataFormat(fMontant);
            montantFort = style(c, police(c, 11, true, null), null);
            montantFort.setDataFormat(fMontant);
            entier = trait(c, style(c, police(c, 10, false, null), null));
            entier.setDataFormat(fEntier);

            total = style(c, police(c, 10, true, null), VERT_PALE);
            total.setBorderTop(BorderStyle.MEDIUM);
            ((XSSFCellStyle) total).setTopBorderColor(new XSSFColor(VERT, null));
            totalMontant = c.createCellStyle();
            totalMontant.cloneStyleFrom(total);
            totalMontant.setDataFormat(fMontant);
        }

        private static XSSFFont police(XSSFWorkbook c, int taille, boolean gras, byte[] couleur) {
            return police(c, taille, gras, couleur, false);
        }

        private static XSSFFont police(XSSFWorkbook c, int taille, boolean gras, byte[] couleur, boolean italique) {
            XSSFFont police = c.createFont();
            police.setFontName("Calibri");
            police.setFontHeightInPoints((short) taille);
            police.setBold(gras);
            police.setItalic(italique);
            if (couleur != null) {
                police.setColor(new XSSFColor(couleur, null));
            }
            return police;
        }

        private static CellStyle style(XSSFWorkbook c, Font police, byte[] fond) {
            XSSFCellStyle style = c.createCellStyle();
            style.setFont(police);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            if (fond != null) {
                style.setFillForegroundColor(new XSSFColor(fond, null));
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            return style;
        }

        /** Un filet discret sous chaque ligne : on suit la ligne sans que la grille crie. */
        private static CellStyle trait(XSSFWorkbook c, CellStyle style) {
            style.setBorderBottom(BorderStyle.THIN);
            ((XSSFCellStyle) style).setBottomBorderColor(new XSSFColor(GRIS_TRAIT, null));
            return style;
        }
    }
}
