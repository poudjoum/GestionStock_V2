package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * L'activite recente de chaque commerce, en deux requetes pour toute la plateforme : les ventes
 * des 30 derniers jours et des 30 d'avant, puis les tickets des huit dernieres semaines.
 *
 * Les semaines sont glissantes — les sept derniers jours, les sept d'avant… — et non calendaires :
 * on compare des durees egales, quel que soit le jour ou l'editeur regarde.
 */
@Component
public class ActivitePlateforme {

    public static final int SEMAINES = 8;

    /** Ce qu'a fait un commerce recemment. `semaines` va de la plus ancienne a la plus recente. */
    public record Activite(long ventes30j, BigDecimal chiffre30j, long ventes30jPrecedents,
                           BigDecimal chiffre30jPrecedents, long[] semaines) {

        static Activite vide() {
            return new Activite(0, BigDecimal.ZERO, 0, BigDecimal.ZERO, new long[SEMAINES]);
        }
    }

    private final EntityManager em;

    public ActivitePlateforme(EntityManager em) {
        this.em = em;
    }

    public Map<Long, Activite> parEntreprise(Instant maintenant) {
        OffsetDateTime m = maintenant.atOffset(ZoneOffset.UTC);
        OffsetDateTime j30 = m.minusDays(30);
        OffsetDateTime j60 = m.minusDays(60);
        OffsetDateTime s8 = m.minusDays(7L * SEMAINES);

        Map<Long, Activite> activites = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> mois = em.createNativeQuery("""
                select v.id_entreprise,
                       count(distinct case when v.date_vente >= :j30 then v.id end),
                       coalesce(sum(case when v.date_vente >= :j30 then l.quantite * l.prix_unitaire end), 0),
                       count(distinct case when v.date_vente < :j30 then v.id end),
                       coalesce(sum(case when v.date_vente < :j30 then l.quantite * l.prix_unitaire end), 0)
                from vente v join ligne_vente l on l.id_vente = v.id
                where v.annulee = false and v.date_vente >= :j60 and v.date_vente <= :maintenant
                  and v.id_entreprise is not null
                group by v.id_entreprise
                """)
                .setParameter("j30", j30).setParameter("j60", j60).setParameter("maintenant", m)
                .getResultList();
        for (Object[] l : mois) {
            activites.put(((Number) l[0]).longValue(), new Activite(((Number) l[1]).longValue(), nombre(l[2]),
                    ((Number) l[3]).longValue(), nombre(l[4]), new long[SEMAINES]));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> semaines = em.createNativeQuery("""
                select v.id_entreprise, floor(extract(epoch from (cast(:maintenant as timestamptz) - v.date_vente)) / 604800), count(*)
                from vente v
                where v.annulee = false and v.date_vente >= :s8 and v.date_vente <= :maintenant
                  and v.id_entreprise is not null
                group by 1, 2
                """)
                .setParameter("s8", s8).setParameter("maintenant", m)
                .getResultList();
        for (Object[] l : semaines) {
            long id = ((Number) l[0]).longValue();
            int ilYa = ((Number) l[1]).intValue();
            if (ilYa < 0 || ilYa >= SEMAINES) {
                continue;
            }
            Activite a = activites.computeIfAbsent(id, k -> Activite.vide());
            a.semaines()[SEMAINES - 1 - ilYa] = ((Number) l[2]).longValue();
        }
        return activites;
    }

    /** Les jours depuis la derniere vente ; nul si le commerce n'a jamais vendu. */
    public static Long joursDepuis(Instant derniereVente, Instant maintenant) {
        return derniereVente == null ? null : ChronoUnit.DAYS.between(derniereVente, maintenant);
    }

    private static BigDecimal nombre(Object valeur) {
        return valeur == null ? BigDecimal.ZERO : new BigDecimal(valeur.toString());
    }
}
