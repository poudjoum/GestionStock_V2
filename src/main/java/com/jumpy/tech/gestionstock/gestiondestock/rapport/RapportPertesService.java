package com.jumpy.tech.gestionstock.gestiondestock.rapport;

import com.jumpy.tech.gestionstock.gestiondestock.analyse.AnalyseArticlesDto;
import com.jumpy.tech.gestionstock.gestiondestock.analyse.AnalyseService;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ou le commerce perd : la demarque, les impayes, le stock qui dort.
 *
 * La demarque est ce qui sort sans etre vendu ni rendu : casse, vol ou perte, peremption, usage
 * interne, et l'ecart d'inventaire — un manquant y ajoute, un surplus en retranche, puisque le
 * logiciel avait compte trop de pertes ailleurs. Un retour au fournisseur n'en est pas : il est
 * rembourse. Chaque mouvement vaut son cout fige, a defaut le cout moyen d'aujourd'hui.
 */
@Service
@Transactional(readOnly = true)
public class RapportPertesService {

    private static final BigDecimal CENT = BigDecimal.valueOf(100);
    /** Le sens d'un mouvement est stocke par son rang : 0 entree, 1 sortie. */
    private static final int SORTIE = 1;
    private static final int JOURS_DORMANTS = 90;

    private static final Map<String, String> LIBELLES = new LinkedHashMap<>();

    static {
        LIBELLES.put("CASSE", "Casse");
        LIBELLES.put("PERTE", "Perte ou vol");
        LIBELLES.put("PEREMPTION", "Péremption");
        LIBELLES.put("INVENTAIRE_MANQUANT", "Manquants d’inventaire");
        LIBELLES.put("INVENTAIRE_SURPLUS", "Surplus d’inventaire");
        LIBELLES.put("CONSOMMATION_INTERNE", "Usage interne");
    }

    private static final String COUTS = """
            left join (select id_article,
                              sum(quantite_livree * prix_unitaire) / nullif(sum(quantite_livree * contenance), 0) as cout
                       from ligne_cmnde_fournisseur where quantite_livree > 0 group by id_article) c
                   on c.id_article = m.id_article
            """;

    private final EntityManager em;
    private final Cloisonnement cloisonnement;
    private final SiteCourant siteCourant;
    private final Calendrier calendrier;
    private final AnalyseService analyseService;

    public RapportPertesService(EntityManager em, Cloisonnement cloisonnement, SiteCourant siteCourant,
                                Calendrier calendrier, AnalyseService analyseService) {
        this.em = em;
        this.cloisonnement = cloisonnement;
        this.siteCourant = siteCourant;
        this.calendrier = calendrier;
        this.analyseService = analyseService;
    }

    public RapportPertesDto pertes(LocalDate debut, LocalDate fin, boolean tousSites) {
        if (debut == null || fin == null || fin.isBefore(debut) || ChronoUnit.DAYS.between(debut, fin) > 731) {
            throw new InvalidEntityException("La période demandée n'est pas valide", ErrorCodes.VENTE_NOT_VALID);
        }
        Long entreprise = cloisonnement.entrepriseCourante();
        Site site = tousSites && siteCourant.voitTousLesSites() ? null : siteCourant.site();
        Long idSite = site == null ? null : site.getId();
        ZoneId fuseau = calendrier.fuseau();
        long jours = ChronoUnit.DAYS.between(debut, fin) + 1;

        return RapportPertesDto.builder()
                .debut(debut)
                .fin(fin)
                .idSite(idSite)
                .nomSite(site == null ? null : site.getNom())
                .demarque(demarque(entreprise, idSite, fuseau, debut, fin, debut.minusDays(jours), debut.minusDays(1)))
                .impayes(impayes(entreprise, idSite))
                .dormants(dormants(tousSites))
                .build();
    }

    // --- La demarque -------------------------------------------------------------------------

    private RapportPertesDto.Demarque demarque(Long entreprise, Long site, ZoneId fuseau, LocalDate debut, LocalDate fin,
                                               LocalDate debutPrecedent, LocalDate finPrecedente) {
        String poste = "case when m.motif = 'INVENTAIRE' then "
                + "case when m.type_mvt = " + SORTIE + " then 'INVENTAIRE_MANQUANT' else 'INVENTAIRE_SURPLUS' end "
                + "else m.motif end";
        List<RapportPertesDto.Poste> parMotif = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        long estimes = 0;
        long sansCout = 0;
        for (Object[] l : mouvements(entreprise, site, fuseau, debut, fin, poste, "''", "")) {
            String cle = (String) l[0];
            BigDecimal valeur = nombre(l[2]);
            total = total.add(valeur);
            estimes += ((Number) l[5]).longValue();
            sansCout += ((Number) l[6]).longValue();
            parMotif.add(RapportPertesDto.Poste.builder()
                    .cle(cle)
                    .libelle(LIBELLES.getOrDefault(cle, cle))
                    .valeur(arrondi(valeur))
                    .quantite(nombre(l[3]))
                    .mouvements(((Number) l[4]).longValue())
                    .build());
        }
        // A valeur egale, l'ordre des motifs reste le meme d'une periode a l'autre.
        List<String> ordre = new ArrayList<>(LIBELLES.keySet());
        parMotif.sort(Comparator.comparing(RapportPertesDto.Poste::getValeur).reversed()
                .thenComparing(p -> ordre.indexOf(p.getCle())));

        List<RapportPertesDto.Poste> parArticle = new ArrayList<>();
        for (Object[] l : mouvements(entreprise, site, fuseau, debut, fin, "cast(m.id_article as varchar)",
                "max(a.designation)", "join article a on a.id = m.id_article")) {
            parArticle.add(RapportPertesDto.Poste.builder()
                    .cle((String) l[0])
                    .libelle((String) l[1])
                    .valeur(arrondi(nombre(l[2])))
                    .quantite(nombre(l[3]))
                    .mouvements(((Number) l[4]).longValue())
                    .build());
        }
        parArticle.sort(Comparator.comparing(RapportPertesDto.Poste::getValeur).reversed());

        BigDecimal precedente = mouvements(entreprise, site, fuseau, debutPrecedent, finPrecedente, "'total'", "''", "")
                .stream().map(l -> nombre(l[2])).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal chiffre = chiffreAffaires(entreprise, site, fuseau, debut, fin);

        return RapportPertesDto.Demarque.builder()
                .valeur(arrondi(total))
                .valeurPrecedente(arrondi(precedente))
                .chiffreAffaires(arrondi(chiffre))
                .tauxDuChiffre(chiffre.signum() == 0 ? null : total.multiply(CENT).divide(chiffre, 1, RoundingMode.HALF_UP))
                .estimee(estimes > 0)
                .sansCout(sansCout)
                .parMotif(parMotif)
                .parArticle(parArticle.stream().limit(10).toList())
                .build();
    }

    /**
     * Les mouvements de demarque de la periode, groupes par `cle`. Colonnes : cle, libelle, valeur
     * (signee : un surplus est negatif), quantite, mouvements, mouvements estimes, sans cout.
     */
    private List<Object[]> mouvements(Long entreprise, Long site, ZoneId fuseau, LocalDate debut, LocalDate fin,
                                      String cle, String libelle, String jointure) {
        String cout = "coalesce(m.cout_unitaire, c.cout)";
        String signe = "(case when m.type_mvt = " + SORTIE + " then 1 else -1 end)";
        String sql = "select " + cle + " as cle, " + libelle + " as libelle, "
                + "coalesce(sum(" + signe + " * m.quantite * " + cout + "), 0), "
                + "coalesce(sum(" + signe + " * m.quantite), 0), "
                + "count(*), "
                + "coalesce(sum(case when m.cout_unitaire is null and c.cout is not null then 1 else 0 end), 0), "
                + "coalesce(sum(case when " + cout + " is null then 1 else 0 end), 0) "
                + "from mvt_stk m " + COUTS + " " + jointure
                + " where m.motif in ('CASSE', 'PERTE', 'PEREMPTION', 'INVENTAIRE', 'CONSOMMATION_INTERNE')"
                + " and m.date_mvt >= :debut and m.date_mvt < :fin"
                + " and (cast(:entreprise as bigint) is null or m.id_entreprise = :entreprise)"
                + " and (cast(:site as bigint) is null or m.id_site = :site)"
                + (cle.startsWith("'") ? "" : " group by 1");
        return executer(sql, entreprise, site, fuseau, debut, fin);
    }

    private BigDecimal chiffreAffaires(Long entreprise, Long site, ZoneId fuseau, LocalDate debut, LocalDate fin) {
        String sql = "select coalesce(sum(l.quantite * l.prix_unitaire), 0), 0 from ligne_vente l "
                + "join vente v on v.id = l.id_vente "
                + "where v.annulee = false and v.date_vente >= :debut and v.date_vente < :fin"
                + " and (cast(:entreprise as bigint) is null or v.id_entreprise = :entreprise)"
                + " and (cast(:site as bigint) is null or v.id_site = :site)";
        List<Object[]> lignes = executer(sql, entreprise, site, fuseau, debut, fin);
        return lignes.isEmpty() ? BigDecimal.ZERO : nombre(lignes.get(0)[0]);
    }

    private List<Object[]> executer(String sql, Long entreprise, Long site, ZoneId fuseau, LocalDate debut, LocalDate fin) {
        Query requete = em.createNativeQuery(sql);
        requete.setParameter("debut", debut.atStartOfDay(fuseau).toOffsetDateTime());
        requete.setParameter("fin", fin.plusDays(1).atStartOfDay(fuseau).toOffsetDateTime());
        requete.setParameter("entreprise", entreprise);
        requete.setParameter("site", site);
        @SuppressWarnings("unchecked")
        List<Object[]> lignes = requete.getResultList();
        return lignes;
    }

    // --- Les impayes -------------------------------------------------------------------------

    /**
     * Ce que les clients doivent encore, aujourd'hui : chaque facture non annulee, moins ses
     * reglements. Le site est celui de la vente facturee.
     */
    private RapportPertesDto.Impayes impayes(Long entreprise, Long site) {
        String sql = """
                select f.id, f.date_emission, f.total_ttc - coalesce(r.paye, 0) as reste, cl.id,
                       coalesce(nullif(trim(coalesce(cl.prenoms, '') || ' ' || coalesce(cl.noms, '')), ''), f.nom_client, 'Client de passage'),
                       cl.telephone
                from facture f
                join vente v on v.id = f.id_vente
                left join client cl on cl.id = f.id_client
                left join (select id_facture, sum(montant) as paye from reglement group by id_facture) r on r.id_facture = f.id
                where f.annulee = false
                  and f.total_ttc - coalesce(r.paye, 0) > 0
                  and (cast(:entreprise as bigint) is null or f.id_entreprise = :entreprise)
                  and (cast(:site as bigint) is null or v.id_site = :site)
                """;
        Query requete = em.createNativeQuery(sql);
        requete.setParameter("entreprise", entreprise);
        requete.setParameter("site", site);
        @SuppressWarnings("unchecked")
        List<Object[]> factures = requete.getResultList();

        LocalDate aujourdhui = calendrier.aujourdhui();
        String[] libelles = {"Moins de 30 jours", "31 à 60 jours", "61 à 90 jours", "Plus de 90 jours"};
        BigDecimal[] montants = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        long[] nombres = new long[4];
        Map<String, RapportPertesDto.ClientDebiteur> parClient = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Object[] f : factures) {
            Instant emise = instant(f[1]);
            BigDecimal reste = nombre(f[2]);
            long age = ChronoUnit.DAYS.between(calendrier.jourDe(emise), aujourdhui);
            int tranche = age <= 30 ? 0 : age <= 60 ? 1 : age <= 90 ? 2 : 3;
            montants[tranche] = montants[tranche].add(reste);
            nombres[tranche]++;
            total = total.add(reste);

            Long idClient = f[3] == null ? null : ((Number) f[3]).longValue();
            String nom = (String) f[4];
            // Les clients de passage se regroupent par le nom porte sur la facture.
            String cle = idClient != null ? "c" + idClient : "n" + nom;
            RapportPertesDto.ClientDebiteur client = parClient.computeIfAbsent(cle, k -> RapportPertesDto.ClientDebiteur.builder()
                    .idClient(idClient).nom(nom).telephone((String) f[5]).du(BigDecimal.ZERO).build());
            client.setDu(client.getDu().add(reste));
            client.setFactures(client.getFactures() + 1);
            if (client.getPlusAncienne() == null || emise.isBefore(client.getPlusAncienne())) {
                client.setPlusAncienne(emise);
                client.setJoursDeRetard(age);
            }
        }

        List<RapportPertesDto.Tranche> tranches = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tranches.add(RapportPertesDto.Tranche.builder().libelle(libelles[i]).montant(montants[i]).factures(nombres[i]).build());
        }
        List<RapportPertesDto.ClientDebiteur> clients = new ArrayList<>(parClient.values());
        // Pas d'arrondi : ce qu'un client doit se compare a ses factures, centimes de TVA compris.
        clients.sort(Comparator.comparing(RapportPertesDto.ClientDebiteur::getDu).reversed());

        return RapportPertesDto.Impayes.builder()
                .total(total)
                .factures(factures.size())
                .clients(clients.size())
                .parAnciennete(tranches)
                .parClient(clients)
                .build();
    }

    // --- Le stock qui dort -------------------------------------------------------------------

    private RapportPertesDto.Dormants dormants(boolean tousSites) {
        AnalyseArticlesDto analyse = analyseService.articles(JOURS_DORMANTS, tousSites);
        List<RapportPertesDto.ArticleDormant> articles = analyse.getArticles().stream()
                .filter(AnalyseArticlesDto.LigneAnalyse::isDormant)
                .sorted(Comparator.comparing(AnalyseArticlesDto.LigneAnalyse::getValeurStock,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(8)
                .map(l -> RapportPertesDto.ArticleDormant.builder()
                        .idArticle(l.getIdArticle())
                        .designation(l.getDesignation())
                        .stock(l.getStock())
                        .valeur(l.getValeurStock())
                        .build())
                .toList();
        return RapportPertesDto.Dormants.builder()
                .jours(JOURS_DORMANTS)
                .nombre(analyse.getNombreDormants())
                .valeur(Objects.requireNonNullElse(analyse.getValeurDormante(), BigDecimal.ZERO))
                .articles(articles)
                .build();
    }

    private static Instant instant(Object valeur) {
        if (valeur instanceof Instant i) return i;
        if (valeur instanceof OffsetDateTime o) return o.toInstant();
        if (valeur instanceof Timestamp t) return t.toInstant();
        return Instant.parse(valeur.toString());
    }

    private static BigDecimal nombre(Object valeur) {
        return valeur == null ? BigDecimal.ZERO : new BigDecimal(valeur.toString());
    }

    private static BigDecimal arrondi(BigDecimal montant) {
        return montant.setScale(0, RoundingMode.HALF_UP);
    }
}
