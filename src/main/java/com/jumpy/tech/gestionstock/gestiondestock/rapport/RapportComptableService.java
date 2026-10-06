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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * La TVA collectee et les journaux, lus sur les factures.
 *
 * Une facture compte a la date de son emission, un reglement a la sienne : c'est ainsi que le
 * comptable les passe. Le site est celui de la vente facturee.
 */
@Service
@Transactional(readOnly = true)
public class RapportComptableService {

    static final Map<String, String> MODES = Map.of(
            "ESPECES", "Espèces",
            "MOBILE_MONEY", "Mobile money",
            "VIREMENT", "Virement",
            "CHEQUE", "Chèque",
            "BON_ACHAT", "Bon d’achat",
            "AUTRE", "Autre");

    private static final String CLIENT = "coalesce(nullif(trim(coalesce(cl.prenoms, '') || ' ' || coalesce(cl.noms, '')), ''), "
            + "f.nom_client, 'Client de passage')";

    private final EntityManager em;
    private final Cloisonnement cloisonnement;
    private final SiteCourant siteCourant;
    private final Calendrier calendrier;

    public RapportComptableService(EntityManager em, Cloisonnement cloisonnement, SiteCourant siteCourant,
                                   Calendrier calendrier) {
        this.em = em;
        this.cloisonnement = cloisonnement;
        this.siteCourant = siteCourant;
        this.calendrier = calendrier;
    }

    public RapportComptableDto comptabilite(LocalDate debut, LocalDate fin, boolean tousSites) {
        if (debut == null || fin == null || fin.isBefore(debut) || ChronoUnit.DAYS.between(debut, fin) > 731) {
            throw new InvalidEntityException("La période demandée n'est pas valide", ErrorCodes.VENTE_NOT_VALID);
        }
        Site site = tousSites && siteCourant.voitTousLesSites() ? null : siteCourant.site();
        Filtre filtre = new Filtre(cloisonnement.entrepriseCourante(), site == null ? null : site.getId(),
                calendrier.fuseau(), debut, fin);

        List<RapportComptableDto.LigneTva> tva = tva(filtre);
        List<RapportComptableDto.LigneJournalVentes> ventes = journalVentes(filtre);
        List<RapportComptableDto.LigneJournalEncaissements> encaissements = journalEncaissements(filtre);
        List<RapportComptableDto.EncaissementParMode> parMode = parMode(encaissements);

        return RapportComptableDto.builder()
                .debut(debut)
                .fin(fin)
                .idSite(filtre.site())
                .nomSite(site == null ? null : site.getNom())
                .tva(tva)
                .totalHt(somme(tva.stream().map(RapportComptableDto.LigneTva::getBaseHt).toList()))
                .totalTva(somme(tva.stream().map(RapportComptableDto.LigneTva::getTva).toList()))
                .totalTtc(somme(tva.stream().map(RapportComptableDto.LigneTva::getTtc).toList()))
                .factures(ventes.stream().filter(v -> !v.isAnnulee()).count())
                .facturesAnnulees(ventes.stream().filter(RapportComptableDto.LigneJournalVentes::isAnnulee).count())
                .totalEncaisse(somme(encaissements.stream().map(RapportComptableDto.LigneJournalEncaissements::getMontant).toList()))
                .encaissementsParMode(parMode)
                .journalVentes(ventes)
                .journalEncaissements(encaissements)
                .build();
    }

    /** La TVA collectee, par taux. Une entreprise non assujettie a sa propre ligne, sans taux. */
    private List<RapportComptableDto.LigneTva> tva(Filtre filtre) {
        String sql = """
                select f.tva_applicable, coalesce(lf.taux_tva, 0),
                       coalesce(sum(lf.montant_ht), 0), coalesce(sum(lf.montant_tva), 0), coalesce(sum(lf.montant_ttc), 0),
                       count(distinct f.id)
                from ligne_facture lf
                join facture f on f.id = lf.id_facture
                join vente v on v.id = f.id_vente
                where f.annulee = false
                """ + Filtre.CONDITIONS + """
                group by 1, 2
                order by 1 desc, 2 desc
                """;
        List<RapportComptableDto.LigneTva> lignes = new ArrayList<>();
        for (Object[] l : filtre.executer(em, sql)) {
            boolean applicable = Boolean.TRUE.equals(l[0]);
            BigDecimal taux = nombre(l[1]);
            lignes.add(RapportComptableDto.LigneTva.builder()
                    .libelle(!applicable ? "TVA non applicable"
                            : taux.signum() == 0 ? "Exonéré (0 %)"
                            : "TVA " + taux.stripTrailingZeros().toPlainString().replace('.', ',') + " %")
                    .taux(applicable ? taux : null)
                    .baseHt(nombre(l[2]))
                    .tva(nombre(l[3]))
                    .ttc(nombre(l[4]))
                    .factures(((Number) l[5]).longValue())
                    .build());
        }
        return lignes;
    }

    /** Une ligne par facture emise, annulees comprises : la numerotation ne doit pas avoir de trou. */
    private List<RapportComptableDto.LigneJournalVentes> journalVentes(Filtre filtre) {
        String sql = "select f.date_emission, f.numero, " + CLIENT + ", f.total_ht, f.total_tva, f.total_ttc, "
                + "coalesce(r.paye, 0), f.annulee "
                + "from facture f join vente v on v.id = f.id_vente "
                + "left join client cl on cl.id = f.id_client "
                + "left join (select id_facture, sum(montant) as paye from reglement group by id_facture) r on r.id_facture = f.id "
                + "where 1 = 1" + Filtre.CONDITIONS
                + " order by f.date_emission, f.numero";
        List<RapportComptableDto.LigneJournalVentes> lignes = new ArrayList<>();
        for (Object[] l : filtre.executer(em, sql)) {
            boolean annulee = Boolean.TRUE.equals(l[7]);
            BigDecimal ttc = nombre(l[5]);
            BigDecimal regle = nombre(l[6]);
            lignes.add(RapportComptableDto.LigneJournalVentes.builder()
                    .date(instant(l[0]))
                    .numero((String) l[1])
                    .client((String) l[2])
                    .totalHt(annulee ? BigDecimal.ZERO : nombre(l[3]))
                    .totalTva(annulee ? BigDecimal.ZERO : nombre(l[4]))
                    .totalTtc(annulee ? BigDecimal.ZERO : ttc)
                    .regle(regle)
                    .reste(annulee ? BigDecimal.ZERO : ttc.subtract(regle).max(BigDecimal.ZERO))
                    .annulee(annulee)
                    .build());
        }
        return lignes;
    }

    /** Une ligne par reglement recu dans la periode, quelle que soit la date de sa facture. */
    private List<RapportComptableDto.LigneJournalEncaissements> journalEncaissements(Filtre filtre) {
        String sql = "select r.date_reglement, f.numero, " + CLIENT + ", r.mode, r.montant, r.reference "
                + "from reglement r join facture f on f.id = r.id_facture join vente v on v.id = f.id_vente "
                + "left join client cl on cl.id = f.id_client "
                + "where r.date_reglement >= :debut and r.date_reglement < :fin"
                + " and (cast(:entreprise as bigint) is null or f.id_entreprise = :entreprise)"
                + " and (cast(:site as bigint) is null or v.id_site = :site)"
                + " order by r.date_reglement, r.id";
        List<RapportComptableDto.LigneJournalEncaissements> lignes = new ArrayList<>();
        for (Object[] l : filtre.executer(em, sql)) {
            lignes.add(RapportComptableDto.LigneJournalEncaissements.builder()
                    .date(instant(l[0]))
                    .numeroFacture((String) l[1])
                    .client((String) l[2])
                    .mode((String) l[3])
                    .montant(nombre(l[4]))
                    .reference((String) l[5])
                    .build());
        }
        return lignes;
    }

    private static List<RapportComptableDto.EncaissementParMode> parMode(List<RapportComptableDto.LigneJournalEncaissements> lignes) {
        Map<String, RapportComptableDto.EncaissementParMode> parMode = new java.util.LinkedHashMap<>();
        for (RapportComptableDto.LigneJournalEncaissements l : lignes) {
            RapportComptableDto.EncaissementParMode m = parMode.computeIfAbsent(l.getMode(), k -> RapportComptableDto.EncaissementParMode.builder()
                    .mode(k).libelle(MODES.getOrDefault(k, k)).montant(BigDecimal.ZERO).build());
            m.setMontant(m.getMontant().add(l.getMontant()));
            m.setNombre(m.getNombre() + 1);
        }
        List<RapportComptableDto.EncaissementParMode> resultat = new ArrayList<>(parMode.values());
        resultat.sort(java.util.Comparator.comparing(RapportComptableDto.EncaissementParMode::getMontant).reversed());
        return resultat;
    }

    private static BigDecimal somme(List<BigDecimal> montants) {
        return montants.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal nombre(Object valeur) {
        return valeur == null ? BigDecimal.ZERO : new BigDecimal(valeur.toString());
    }

    private static Instant instant(Object valeur) {
        if (valeur instanceof Instant i) return i;
        if (valeur instanceof OffsetDateTime o) return o.toInstant();
        if (valeur instanceof Timestamp t) return t.toInstant();
        return Instant.parse(valeur.toString());
    }

    /** L'entreprise, le site et la periode, appliques aux factures par leur date d'emission. */
    private record Filtre(Long entreprise, Long site, ZoneId fuseau, LocalDate debut, LocalDate fin) {

        static final String CONDITIONS = """
                 and f.date_emission >= :debut and f.date_emission < :fin
                 and (cast(:entreprise as bigint) is null or f.id_entreprise = :entreprise)
                 and (cast(:site as bigint) is null or v.id_site = :site)
                """;

        List<Object[]> executer(EntityManager em, String sql) {
            Query requete = em.createNativeQuery(sql);
            requete.setParameter("debut", debut.atStartOfDay(fuseau).toOffsetDateTime());
            requete.setParameter("fin", fin.plusDays(1).atStartOfDay(fuseau).toOffsetDateTime());
            requete.setParameter("entreprise", entreprise);
            requete.setParameter("site", site);
            @SuppressWarnings("unchecked")
            List<Object[]> lignes = requete.getResultList();
            return lignes;
        }
    }
}
