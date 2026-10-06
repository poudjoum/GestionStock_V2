package com.jumpy.tech.gestionstock.gestiondestock.rapport;

import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Le courriel du resume : un sujet, un texte, et sa version mise en forme.
 *
 * Le HTML est ecrit pour les messageries, pas pour un navigateur : des tableaux plutot que des
 * grilles, des styles en ligne, 600 pixels de large, aucune image a telecharger. Une hausse et une
 * baisse se disent par une fleche et un signe, pas par la seule couleur.
 */
public record ResumeCourriel(String sujet, String texte, String html) {

    private static final Locale FR = Locale.FRANCE;
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("EEEE d MMMM", FR);
    private static final DateTimeFormatter COURT = DateTimeFormatter.ofPattern("d MMM", FR);
    private static final String VERT = "#1d6b43";
    private static final String VERT_PALE = "#f0f7f2";
    private static final String ENCRE = "#17211b";
    private static final String ENCRE_2 = "#47544c";
    private static final String TRAIT = "#d9dfd9";
    private static final String HAUSSE = "#1e7a45";
    private static final String BAISSE = "#b42318";

    /** Ce qu'il faut pour ecrire le courriel, deja calcule. */
    public record Contenu(String commerce, ResumeEnvoye.Type type, LocalDate debut, LocalDate fin,
                          RapportVentesDto ventes, RapportPertesDto pertes, String adressePublique) {
    }

    public static ResumeCourriel ecrire(Contenu c) {
        boolean semaine = c.type() == ResumeEnvoye.Type.HEBDO;
        String periode = semaine
                ? "du " + COURT.format(c.debut()) + " au " + COURT.format(c.fin())
                : JOUR.format(c.debut());
        String reference = semaine ? "la semaine précédente" : "la veille";
        String sujet = (semaine ? "Votre semaine " + periode : "Vos ventes " + ("du " + periode)) + " — " + c.commerce();

        RapportVentesDto.Indicateurs v = c.ventes().getCourant();
        RapportVentesDto.Indicateurs p = c.ventes().getPrecedent();
        RapportPertesDto.Demarque d = c.pertes().getDemarque();
        RapportPertesDto.Impayes i = c.pertes().getImpayes();
        RapportPertesDto.Dormants z = c.pertes().getDormants();
        BigDecimal impayesAnciens = i.getParAnciennete().stream().skip(2)
                .map(RapportPertesDto.Tranche::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<RapportVentesDto.Repartition> rayons = c.ventes().getParCategorie().stream().limit(3).toList();
        String lien = StringUtils.hasText(c.adressePublique())
                ? c.adressePublique().replaceAll("/+$", "") + "/rapports" : null;

        // --- Le texte, pour les messageries sans HTML ---
        StringBuilder t = new StringBuilder();
        t.append(c.commerce()).append(" — ").append(semaine ? "votre semaine " : "vos ventes du ").append(periode).append("\n\n");
        t.append("Chiffre d'affaires HT : ").append(f(v.getChiffreAffaires())).append(" F (").append(ecartTexte(v.getChiffreAffaires(), p.getChiffreAffaires())).append(" vs ").append(reference).append(")\n");
        t.append("Marge : ").append(v.getTauxMarge() == null ? "inconnue, faute de coût d'achat" : f(v.getMarge()) + " F (" + pct(v.getTauxMarge()) + " du chiffre)").append("\n");
        t.append("Tickets : ").append(v.getTickets()).append(" — panier moyen ").append(f(v.getPanierMoyen())).append(" F\n\n");
        t.append("À surveiller\n");
        t.append("- Démarque : ").append(f(d.getValeur())).append(" F").append(d.getTauxDuChiffre() == null ? "" : " (" + pct(d.getTauxDuChiffre()) + " du chiffre)").append("\n");
        t.append("- Impayés à ce jour : ").append(f(i.getTotal())).append(" F").append(impayesAnciens.signum() > 0 ? ", dont " + f(impayesAnciens) + " F depuis plus de 60 jours" : "").append("\n");
        t.append("- Stock qui dort : ").append(f(z.getValeur())).append(" F (").append(nombre(z.getNombre(), "article")).append(")\n");
        if (!rayons.isEmpty()) {
            t.append("\nLes rayons qui ont fait ").append(semaine ? "la semaine" : "la journée").append("\n");
            rayons.forEach(r -> t.append("- ").append(r.getLibelle()).append(" : ").append(f(r.getChiffreAffaires())).append(" F\n"));
        }
        if (v.isEstimee()) {
            t.append("\nMarge estimée en partie : des ventes d'avant les rapports sont comptées au coût moyen actuel.\n");
        }
        if (lien != null) {
            t.append("\nTous les rapports : ").append(lien).append("\n");
        }
        t.append("\nVous recevez ce résumé en tant qu'administrateur ou gérant de ").append(c.commerce())
                .append(". Il se règle dans Paramètres › Résumés par courriel.\n");

        // --- Le HTML ---
        StringBuilder h = new StringBuilder();
        h.append("<!doctype html><html lang=\"fr\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\">")
                .append("<title>").append(e(sujet)).append("</title></head>")
                .append("<body style=\"margin:0;padding:0;background:#f6f7f3;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f6f7f3;\"><tr><td align=\"center\" style=\"padding:24px 12px;\">")
                .append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;max-width:600px;background:#ffffff;border:1px solid ").append(TRAIT).append(";border-radius:12px;font-family:Arial,Helvetica,sans-serif;color:").append(ENCRE).append(";\">");

        // En-tete
        h.append("<tr><td style=\"background:").append(VERT).append(";border-radius:12px 12px 0 0;padding:20px 24px;color:#ffffff;\">")
                .append("<div style=\"font-size:13px;opacity:.85;\">").append(e(c.commerce())).append("</div>")
                .append("<div style=\"font-size:22px;font-weight:bold;margin-top:4px;\">")
                .append(semaine ? "Votre semaine" : "Vos ventes d’hier").append("</div>")
                .append("<div style=\"font-size:14px;margin-top:2px;\">").append(e(periode)).append("</div></td></tr>");

        // Les quatre chiffres
        h.append("<tr><td style=\"padding:20px 24px 4px;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">");
        h.append("<tr>")
                .append(tuile("Chiffre d’affaires HT", f(v.getChiffreAffaires()) + " F", ecartHtml(v.getChiffreAffaires(), p.getChiffreAffaires(), reference, false)))
                // Sans cout connu, la marge n'est pas nulle : elle est inconnue, et la dire « 0 F » tromperait.
                .append(tuile("Marge", v.getTauxMarge() == null ? "—" : f(v.getMarge()) + " F",
                        v.getTauxMarge() == null ? "Coût d’achat inconnu" : pct(v.getTauxMarge()) + " du chiffre"))
                .append("</tr><tr>")
                .append(tuile("Tickets", String.valueOf(v.getTickets()), ecartHtml(BigDecimal.valueOf(v.getTickets()), BigDecimal.valueOf(p.getTickets()), reference, false)))
                .append(tuile("Panier moyen", f(v.getPanierMoyen()) + " F", ecartHtml(v.getPanierMoyen(), p.getPanierMoyen(), reference, false)))
                .append("</tr></table></td></tr>");

        // A surveiller
        h.append("<tr><td style=\"padding:12px 24px 4px;\">").append(titre("À surveiller"))
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"font-size:14px;\">")
                .append(ligne("Démarque", f(d.getValeur()) + " F", d.getTauxDuChiffre() == null ? null : pct(d.getTauxDuChiffre()) + " du chiffre"))
                .append(ligne("Impayés à ce jour", f(i.getTotal()) + " F", impayesAnciens.signum() > 0
                        ? "dont " + f(impayesAnciens) + " F depuis plus de 60 jours" : nombre(i.getClients(), "client")))
                .append(ligne("Stock qui dort", f(z.getValeur()) + " F", nombre(z.getNombre(), "article") + " sans vente depuis " + z.getJours() + " jours"))
                .append("</table></td></tr>");

        // Les rayons
        if (!rayons.isEmpty()) {
            h.append("<tr><td style=\"padding:12px 24px 4px;\">").append(titre(semaine ? "Les rayons de la semaine" : "Les rayons de la journée"))
                    .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"font-size:14px;\">");
            for (RapportVentesDto.Repartition r : rayons) {
                h.append(ligne(r.getLibelle(), f(r.getChiffreAffaires()) + " F", r.getPart() == null ? null : pct(r.getPart()) + " du chiffre"));
            }
            h.append("</table></td></tr>");
        }

        if (v.isEstimee()) {
            h.append("<tr><td style=\"padding:8px 24px 0;font-size:12px;color:").append(ENCRE_2).append(";\">")
                    .append("Marge estimée en partie : des ventes d’avant les rapports sont comptées au coût d’achat moyen actuel.</td></tr>");
        }

        // Le bouton
        if (lien != null) {
            h.append("<tr><td align=\"center\" style=\"padding:20px 24px 8px;\">")
                    .append("<a href=\"").append(e(lien)).append("\" style=\"display:inline-block;background:").append(VERT)
                    .append(";color:#ffffff;text-decoration:none;font-weight:bold;font-size:14px;padding:12px 24px;border-radius:999px;\">Ouvrir les rapports</a></td></tr>");
        }

        // Le pied
        h.append("<tr><td style=\"padding:16px 24px 20px;font-size:12px;line-height:1.5;color:").append(ENCRE_2)
                .append(";border-top:1px solid ").append(TRAIT).append(";\">")
                .append("Vous recevez ce résumé en tant qu’administrateur ou gérant de ").append(e(c.commerce()))
                .append(". Il se règle dans <b>Paramètres › Résumés par courriel</b>.</td></tr>");
        h.append("</table></td></tr></table></body></html>");

        return new ResumeCourriel(sujet, t.toString(), h.toString());
    }

    // --- Les morceaux ------------------------------------------------------------------------

    private static String tuile(String libelle, String valeur, String sousTexte) {
        return "<td width=\"50%\" valign=\"top\" style=\"padding:0 6px 12px 0;\">"
                + "<div style=\"background:" + VERT_PALE + ";border-radius:10px;padding:12px 14px;\">"
                + "<div style=\"font-size:11px;letter-spacing:.06em;text-transform:uppercase;color:" + ENCRE_2 + ";\">" + e(libelle) + "</div>"
                + "<div style=\"font-size:22px;font-weight:bold;margin:4px 0 2px;color:" + ENCRE + ";\">" + e(valeur) + "</div>"
                + "<div style=\"font-size:12px;color:" + ENCRE_2 + ";\">" + sousTexte + "</div></div></td>";
    }

    private static String titre(String texte) {
        return "<div style=\"font-size:13px;font-weight:bold;color:" + VERT + ";text-transform:uppercase;letter-spacing:.06em;margin:4px 0 8px;\">"
                + e(texte) + "</div>";
    }

    private static String ligne(String libelle, String valeur, String detail) {
        return "<tr><td style=\"padding:8px 0;border-bottom:1px solid " + TRAIT + ";\">" + e(libelle)
                + (detail == null ? "" : "<div style=\"font-size:12px;color:" + ENCRE_2 + ";\">" + e(detail) + "</div>")
                + "</td><td align=\"right\" style=\"padding:8px 0;border-bottom:1px solid " + TRAIT + ";font-weight:bold;white-space:nowrap;\">"
                + e(valeur) + "</td></tr>";
    }

    /** « ▲ +12,5 % vs la semaine précédente », vert ; une baisse en rouge ; rien a comparer, un tiret. */
    private static String ecartHtml(BigDecimal valeur, BigDecimal reference, String vs, boolean baisseFavorable) {
        if (valeur == null || reference == null || reference.signum() == 0) {
            return "— vs " + e(vs);
        }
        double ecart = (valeur.doubleValue() - reference.doubleValue()) / Math.abs(reference.doubleValue()) * 100;
        boolean hausse = ecart >= 0;
        boolean bon = hausse != baisseFavorable;
        return "<span style=\"color:" + (bon ? HAUSSE : BAISSE) + ";font-weight:bold;\">" + (hausse ? "▲ +" : "▼ ")
                + pctBrut(ecart) + "</span> vs " + e(vs);
    }

    private static String ecartTexte(BigDecimal valeur, BigDecimal reference) {
        if (valeur == null || reference == null || reference.signum() == 0) {
            return "—";
        }
        double ecart = (valeur.doubleValue() - reference.doubleValue()) / Math.abs(reference.doubleValue()) * 100;
        return (ecart >= 0 ? "+" : "") + pctBrut(ecart);
    }

    /** « aucun client », « 1 client », « 3 clients ». */
    private static String nombre(long n, String mot) {
        return n == 0 ? "aucun " + mot : n + " " + mot + (n > 1 ? "s" : "");
    }

    private static String f(BigDecimal montant) {
        return NumberFormat.getIntegerInstance(FR).format(montant == null ? 0 : montant.setScale(0, java.math.RoundingMode.HALF_UP));
    }

    private static String pct(BigDecimal valeur) {
        return pctBrut(valeur.doubleValue());
    }

    private static String pctBrut(double valeur) {
        NumberFormat format = NumberFormat.getNumberInstance(FR);
        format.setMaximumFractionDigits(1);
        return format.format(valeur) + " %";
    }

    private static String e(String texte) {
        return texte == null ? "" : HtmlUtils.htmlEscape(texte, "UTF-8");
    }
}
