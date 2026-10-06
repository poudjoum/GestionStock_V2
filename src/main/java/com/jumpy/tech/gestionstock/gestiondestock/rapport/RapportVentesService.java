package com.jumpy.tech.gestionstock.gestiondestock.rapport;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Site;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.promotion.Calendrier;
import com.jumpy.tech.gestionstock.gestiondestock.site.SiteCourant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Le rapport des ventes : chiffre d'affaires, marge, tickets, et ou ils se font.
 *
 * Tout est agrege par la base, une requete par decoupage : un an de ventes d'un commerce actif fait
 * des centaines de milliers de lignes, qu'il serait absurde de remonter une a une pour les
 * additionner en Java. Les journees se decoupent a l'heure du magasin, et non a celle du serveur.
 *
 * La marge d'une ligne vaut son chiffre d'affaires moins quantite x contenance x cout. Le cout est
 * celui fige a la vente ; a defaut — les ventes d'avant —, le cout moyen d'aujourd'hui, et le
 * rapport le dit. Une ligne dont l'article n'a jamais ete achete par une commande n'a pas de cout
 * du tout : elle compte dans le chiffre d'affaires, pas dans la marge.
 */
@Service
@Transactional(readOnly = true)
public class RapportVentesService {

    private static final BigDecimal CENT = BigDecimal.valueOf(100);
    private static final int JOURS_MAX = 731;
    /** Au-dela, la courbe passe au mois : 365 points ne se lisent plus. */
    private static final int JOURS_PAR_JOUR = 62;

    /** Le cout moyen actuel, pour les lignes d'avant que le cout soit fige. */
    private static final String COUTS = """
            left join (select id_article,
                              sum(quantite_livree * prix_unitaire) / nullif(sum(quantite_livree * contenance), 0) as cout
                       from ligne_cmnde_fournisseur where quantite_livree > 0 group by id_article) c
                   on c.id_article = l.id_article
            """;

    private final EntityManager em;
    private final Cloisonnement cloisonnement;
    private final SiteCourant siteCourant;
    private final Calendrier calendrier;

    public RapportVentesService(EntityManager em, Cloisonnement cloisonnement, SiteCourant siteCourant,
                                Calendrier calendrier) {
        this.em = em;
        this.cloisonnement = cloisonnement;
        this.siteCourant = siteCourant;
        this.calendrier = calendrier;
    }

    public RapportVentesDto ventes(LocalDate debut, LocalDate fin, boolean tousSites) {
        if (debut == null || fin == null || fin.isBefore(debut)) {
            throw new InvalidEntityException("La période demandée n'est pas valide", ErrorCodes.VENTE_NOT_VALID);
        }
        long jours = ChronoUnit.DAYS.between(debut, fin) + 1;
        if (jours > JOURS_MAX) {
            throw new InvalidEntityException("Un rapport couvre au plus deux ans", ErrorCodes.VENTE_NOT_VALID);
        }
        Long entreprise = cloisonnement.entrepriseCourante();
        Site site = tousSites && siteCourant.voitTousLesSites() ? null : siteCourant.site();
        Filtre filtre = new Filtre(entreprise, site == null ? null : site.getId(), calendrier.fuseau());

        LocalDate debutPrecedent = debut.minusDays(jours);
        LocalDate finPrecedente = debut.minusDays(1);
        boolean parMois = jours > JOURS_PAR_JOUR;
        String cleTemps = parMois
                ? "to_char(v.date_vente at time zone :tz, 'YYYY-MM')"
                : "to_char(v.date_vente at time zone :tz, 'YYYY-MM-DD')";

        Indicateurs courant = indicateurs(filtre, debut, fin);
        return RapportVentesDto.builder()
                .debut(debut)
                .fin(fin)
                .pas(parMois ? "MOIS" : "JOUR")
                .idSite(site == null ? null : site.getId())
                .nomSite(site == null ? null : site.getNom())
                .courant(courant.dto(debut, fin))
                .precedent(indicateurs(filtre, debutPrecedent, finPrecedente).dto(debutPrecedent, finPrecedente))
                .anneePrecedente(indicateurs(filtre, debut.minusYears(1), fin.minusYears(1))
                        .dto(debut.minusYears(1), fin.minusYears(1)))
                .serie(serie(filtre, debut, fin, cleTemps, parMois))
                .seriePrecedente(serie(filtre, debutPrecedent, finPrecedente, cleTemps, parMois))
                .parCategorie(repartition(filtre, debut, fin, courant.ca(),
                        "a.id_category", "coalesce(max(cat.designation), 'Sans catégorie')",
                        "left join category cat on cat.id = a.id_category"))
                .parSite(repartition(filtre, debut, fin, courant.ca(),
                        "v.id_site", "coalesce(max(s.nom), 'Sans site')", "left join site s on s.id = v.id_site"))
                .parVendeur(repartition(filtre, debut, fin, courant.ca(),
                        "v.id_vendeur",
                        "coalesce(max(nullif(trim(coalesce(u.prenoms, '') || ' ' || coalesce(u.nom, '')), '')), max(u.username), 'Non noté')",
                        "left join utilisateur u on u.id = v.id_vendeur"))
                .parHeure(cycle(filtre, debut, fin, "extract(hour from v.date_vente at time zone :tz)", 0, 23))
                .parJourSemaine(cycle(filtre, debut, fin, "extract(isodow from v.date_vente at time zone :tz)", 1, 7))
                .build();
    }

    // --- Les agregats ------------------------------------------------------------------------

    private Indicateurs indicateurs(Filtre filtre, LocalDate debut, LocalDate fin) {
        List<Object[]> lignes = executer(filtre, debut, fin, "'total'", "''", "");
        return lignes.isEmpty() ? Indicateurs.VIDE : Indicateurs.de(lignes.get(0));
    }

    /** Un point par jour ou par mois, y compris ceux sans vente : la courbe ne saute pas. */
    private List<RapportVentesDto.PointVentes> serie(Filtre filtre, LocalDate debut, LocalDate fin,
                                                    String cleTemps, boolean parMois) {
        Map<String, Indicateurs> parCle = new HashMap<>();
        for (Object[] ligne : executer(filtre, debut, fin, cleTemps, "''", "")) {
            parCle.put((String) ligne[0], Indicateurs.de(ligne));
        }
        List<RapportVentesDto.PointVentes> points = new ArrayList<>();
        if (parMois) {
            for (YearMonth m = YearMonth.from(debut); !m.isAfter(YearMonth.from(fin)); m = m.plusMonths(1)) {
                points.add(point(m.toString(), parCle.get(m.toString())));
            }
        } else {
            for (LocalDate j = debut; !j.isAfter(fin); j = j.plusDays(1)) {
                points.add(point(j.toString(), parCle.get(j.toString())));
            }
        }
        return points;
    }

    /** Les heures de la journee ou les jours de la semaine, tous presents. */
    private List<RapportVentesDto.PointVentes> cycle(Filtre filtre, LocalDate debut, LocalDate fin,
                                                    String expression, int premier, int dernier) {
        Map<Integer, Indicateurs> parValeur = new HashMap<>();
        for (Object[] ligne : executer(filtre, debut, fin, expression, "''", "")) {
            parValeur.put(((Number) ligne[0]).intValue(), Indicateurs.de(ligne));
        }
        List<RapportVentesDto.PointVentes> points = new ArrayList<>();
        for (int i = premier; i <= dernier; i++) {
            points.add(point(String.valueOf(i), parValeur.get(i)));
        }
        return points;
    }

    private List<RapportVentesDto.Repartition> repartition(Filtre filtre, LocalDate debut, LocalDate fin,
                                                          BigDecimal total, String cle, String libelle,
                                                          String jointure) {
        List<RapportVentesDto.Repartition> parts = new ArrayList<>();
        for (Object[] ligne : executer(filtre, debut, fin, cle, libelle, jointure)) {
            Indicateurs i = Indicateurs.de(ligne);
            parts.add(RapportVentesDto.Repartition.builder()
                    .id(ligne[0] == null ? null : ((Number) ligne[0]).longValue())
                    .libelle((String) ligne[1])
                    .chiffreAffaires(arrondi(i.ca()))
                    .marge(arrondi(i.marge()))
                    .tauxMarge(taux(i.marge(), i.caCouvert()))
                    .tickets(i.tickets())
                    .part(taux(i.ca(), total))
                    .build());
        }
        parts.sort(Comparator.comparing(RapportVentesDto.Repartition::getChiffreAffaires).reversed());
        return parts;
    }

    /**
     * La requete commune : les lignes des ventes non annulees de la periode, groupees par `cle`.
     * Colonnes rendues : cle, libelle, chiffre d'affaires, marge, CA couvert, tickets, lignes
     * estimees.
     */
    private List<Object[]> executer(Filtre filtre, LocalDate debut, LocalDate fin, String cle, String libelle,
                                    String jointure) {
        // Par position et non par expression : le fuseau, passe en parametre, ferait de l'expression
        // du select et de celle du group by deux expressions differentes aux yeux de PostgreSQL.
        String groupe = cle.startsWith("'") ? "" : " group by 1";
        String sql = "select " + cle + " as cle, " + libelle + " as libelle, "
                + "coalesce(sum(l.quantite * l.prix_unitaire), 0), "
                + "coalesce(sum(case when coalesce(l.cout_unitaire, c.cout) is not null "
                + "  then l.quantite * l.prix_unitaire - l.quantite * l.contenance * coalesce(l.cout_unitaire, c.cout) end), 0), "
                + "coalesce(sum(case when coalesce(l.cout_unitaire, c.cout) is not null then l.quantite * l.prix_unitaire end), 0), "
                + "count(distinct v.id), "
                + "coalesce(sum(case when l.cout_unitaire is null and c.cout is not null then 1 else 0 end), 0) "
                + "from ligne_vente l join vente v on v.id = l.id_vente join article a on a.id = l.id_article "
                + COUTS + " " + jointure
                + " where v.annulee = false and v.date_vente >= :debut and v.date_vente < :fin"
                + " and (cast(:entreprise as bigint) is null or v.id_entreprise = :entreprise)"
                + " and (cast(:site as bigint) is null or v.id_site = :site)"
                + groupe;
        Query requete = em.createNativeQuery(sql);
        requete.setParameter("debut", debut.atStartOfDay(filtre.fuseau()).toOffsetDateTime());
        requete.setParameter("fin", fin.plusDays(1).atStartOfDay(filtre.fuseau()).toOffsetDateTime());
        requete.setParameter("entreprise", filtre.entreprise());
        requete.setParameter("site", filtre.site());
        if (sql.contains(":tz")) {
            requete.setParameter("tz", filtre.fuseau().getId());
        }
        @SuppressWarnings("unchecked")
        List<Object[]> lignes = requete.getResultList();
        return lignes;
    }

    // --- Mise en forme -----------------------------------------------------------------------

    private static RapportVentesDto.PointVentes point(String cle, Indicateurs i) {
        Indicateurs v = i == null ? Indicateurs.VIDE : i;
        return RapportVentesDto.PointVentes.builder()
                .cle(cle)
                .chiffreAffaires(arrondi(v.ca()))
                .marge(arrondi(v.marge()))
                .tickets(v.tickets())
                .build();
    }

    private static BigDecimal arrondi(BigDecimal montant) {
        return montant.setScale(0, RoundingMode.HALF_UP);
    }

    private static BigDecimal taux(BigDecimal partie, BigDecimal tout) {
        return tout == null || tout.signum() == 0 ? null : partie.multiply(CENT).divide(tout, 1, RoundingMode.HALF_UP);
    }

    private record Filtre(Long entreprise, Long site, ZoneId fuseau) {
    }

    private record Indicateurs(BigDecimal ca, BigDecimal marge, BigDecimal caCouvert, long tickets, long estimees) {

        static final Indicateurs VIDE = new Indicateurs(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);

        static Indicateurs de(Object[] ligne) {
            return new Indicateurs(nombre(ligne[2]), nombre(ligne[3]), nombre(ligne[4]),
                    ((Number) ligne[5]).longValue(), ((Number) ligne[6]).longValue());
        }

        RapportVentesDto.Indicateurs dto(LocalDate debut, LocalDate fin) {
            return RapportVentesDto.Indicateurs.builder()
                    .debut(debut)
                    .fin(fin)
                    .chiffreAffaires(arrondi(ca))
                    .marge(arrondi(marge))
                    .caCouvert(arrondi(caCouvert))
                    .tauxMarge(taux(marge, caCouvert))
                    .tickets(tickets)
                    .panierMoyen(tickets == 0 ? BigDecimal.ZERO : ca.divide(BigDecimal.valueOf(tickets), 0, RoundingMode.HALF_UP))
                    .estimee(estimees > 0)
                    .build();
        }

        private static BigDecimal nombre(Object valeur) {
            return valeur == null ? BigDecimal.ZERO : new BigDecimal(valeur.toString());
        }
    }
}
