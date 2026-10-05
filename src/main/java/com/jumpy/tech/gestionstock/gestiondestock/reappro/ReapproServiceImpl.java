package com.jumpy.tech.gestionstock.gestiondestock.reappro;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeFourDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ConditionnementDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCmndeFournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ArticleSite;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Conditionnement;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Entreprise;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;
import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneCmndeFournisseur;
import com.jumpy.tech.gestionstock.gestiondestock.entities.MotifMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Site;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.entities.UniteMesure;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleSiteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EntrepriseRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneCmndeFourRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.MvtStkRepository;
import com.jumpy.tech.gestionstock.gestiondestock.reservation.Reservations;
import com.jumpy.tech.gestionstock.gestiondestock.service.CommandeFourService;
import com.jumpy.tech.gestionstock.gestiondestock.site.SiteCourant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Le reapprovisionnement, calcule sur les ventes.
 *
 * Pour chaque article, le site doit pouvoir tenir `joursCouverture` jours au rythme des ventes des
 * trente derniers jours, plus son seuil d'alerte en securite. On retire ce qui est disponible — le
 * stock moins ce que les commandes clients retiennent — et ce qui est deja commande, brouillons
 * compris. Ce qui manque est propose dans l'unite du dernier achat, chez le fournisseur du dernier
 * achat, arrondi a l'unite entiere au-dessus : on ne commande pas 2,3 cartons.
 *
 * Un article qui ne se vend pas et n'a pas de seuil n'est jamais propose : rien ne dit qu'on en
 * veut encore. Un article sous son seuil sans aucune vente l'est, pour remonter au seuil.
 */
@Service
@Slf4j
public class ReapproServiceImpl implements ReapproService {

    static final int JOURS_OBSERVES = 30;
    private static final List<MotifMvtStk> MOTIFS_DE_VENTE =
            List.of(MotifMvtStk.VENTE, MotifMvtStk.CORRECTION_VENTE, MotifMvtStk.ANNULATION_VENTE);
    private static final List<EtatCommande> ATTENDUES =
            List.of(EtatCommande.EN_PREPARATION, EtatCommande.VALIDEE, EtatCommande.PARTIELLEMENT_LIVREE);
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("yyMMdd");

    private final ArticleRepository articleRepository;
    private final ArticleSiteRepository articleSiteRepository;
    private final MvtStkRepository mvtStkRepository;
    private final LigneCmndeFourRepository ligneCmndeFourRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final Reservations reservations;
    private final CommandeFourService commandeFourService;
    private final SiteCourant siteCourant;
    private final Cloisonnement cloisonnement;

    public ReapproServiceImpl(ArticleRepository articleRepository, ArticleSiteRepository articleSiteRepository,
                              MvtStkRepository mvtStkRepository, LigneCmndeFourRepository ligneCmndeFourRepository,
                              EntrepriseRepository entrepriseRepository, Reservations reservations,
                              CommandeFourService commandeFourService, SiteCourant siteCourant,
                              Cloisonnement cloisonnement) {
        this.articleRepository = articleRepository;
        this.articleSiteRepository = articleSiteRepository;
        this.mvtStkRepository = mvtStkRepository;
        this.ligneCmndeFourRepository = ligneCmndeFourRepository;
        this.entrepriseRepository = entrepriseRepository;
        this.reservations = reservations;
        this.commandeFourService = commandeFourService;
        this.siteCourant = siteCourant;
        this.cloisonnement = cloisonnement;
    }

    @Override
    @Transactional(readOnly = true)
    public ReapproDto proposition() {
        Long idEntreprise = cloisonnement.entrepriseCourante();
        Site site = siteCourant.site();
        if (idEntreprise == null || site == null) {
            return ReapproDto.builder().joursObserves(JOURS_OBSERVES).lignes(List.of()).build();
        }
        int couverture = entrepriseRepository.findById(idEntreprise).map(Entreprise::getJoursCouverture).orElse(15);

        List<Article> articles = articleRepository.findAllByIdEntreprise(idEntreprise);
        List<Long> ids = articles.stream().map(Article::getId).toList();
        Map<Long, BigDecimal> stocks = parArticle(ids.isEmpty() ? List.of()
                : mvtStkRepository.stocksReelsDansSite(ids, site.getId(), TypeMvtStk.ENTREE));
        Map<Long, BigDecimal> vendus = parArticle(mvtStkRepository.ventesNettesDansSite(site.getId(),
                Instant.now().minus(JOURS_OBSERVES, ChronoUnit.DAYS), MOTIFS_DE_VENTE, TypeMvtStk.SORTIE));
        Map<Long, BigDecimal> enCommande = parArticle(ligneCmndeFourRepository.enCommandeDansSite(site.getId(), ATTENDUES));
        Map<Long, BigDecimal> seuilsDuSite = ids.isEmpty() ? Map.of()
                : articleSiteRepository.findAllBySiteIdAndArticleIdIn(site.getId(), ids).stream()
                        .filter(l -> l.getSeuilAlerte() != null)
                        .collect(Collectors.toMap(l -> l.getArticle().getId(), ArticleSite::getSeuilAlerte));
        Map<Long, Map<Long, BigDecimal>> reserves = reservations.parArticleEtSite(ids);
        Map<Long, LigneCmndeFournisseur> dernierAchat = new HashMap<>();
        if (!ids.isEmpty()) {
            for (LigneCmndeFournisseur achat : ligneCmndeFourRepository.achatsRecents(ids)) {
                dernierAchat.putIfAbsent(achat.getArticles().getId(), achat);
            }
        }

        BigDecimal jours = BigDecimal.valueOf(JOURS_OBSERVES);
        List<ReapproDto.LigneReappro> lignes = new ArrayList<>();
        for (Article article : articles) {
            Long id = article.getId();
            BigDecimal stock = stocks.getOrDefault(id, BigDecimal.ZERO);
            BigDecimal reserve = reserves.getOrDefault(id, Map.of()).getOrDefault(site.getId(), BigDecimal.ZERO);
            BigDecimal disponible = stock.subtract(reserve);
            BigDecimal vendu = vendus.getOrDefault(id, BigDecimal.ZERO).max(BigDecimal.ZERO);
            BigDecimal parJour = vendu.divide(jours, 4, RoundingMode.HALF_UP);
            BigDecimal seuil = seuilsDuSite.getOrDefault(id, article.getSeuilAlerte());
            if (parJour.signum() == 0 && seuil == null) {
                continue;
            }
            BigDecimal cible = parJour.multiply(BigDecimal.valueOf(couverture))
                    .add(seuil == null ? BigDecimal.ZERO : seuil);
            BigDecimal attendu = enCommande.getOrDefault(id, BigDecimal.ZERO);
            BigDecimal besoin = cible.subtract(disponible.max(BigDecimal.ZERO)).subtract(attendu);
            if (besoin.signum() <= 0) {
                continue;
            }

            LigneCmndeFournisseur achat = dernierAchat.get(id);
            Conditionnement unite = achat == null ? null : achat.getConditionnement();
            if (unite != null && (!unite.isActif() || !unite.isAchetable())) {
                unite = null;
            }
            BigDecimal contenance = unite == null ? BigDecimal.ONE : unite.getQuantiteUnites();
            BigDecimal quantite = arrondir(besoin.divide(contenance, 4, RoundingMode.HALF_UP),
                    unite != null || article.getUniteBase() == UniteMesure.PIECE);
            BigDecimal prix = null;
            if (achat != null && achat.getPrixUnitaire() != null) {
                // Le dernier prix, ramene a l'unite proposee s'il etait dans une autre.
                prix = unite != null && achat.getConditionnement() != null
                        ? achat.getPrixUnitaire()
                        : achat.getPrixUnitaire().divide(achat.getContenance(), 2, RoundingMode.HALF_UP);
            }

            lignes.add(ReapproDto.LigneReappro.builder()
                    .idArticle(id)
                    .codeArticle(article.getCodeArticle())
                    .designation(article.getDesignation())
                    .uniteBase(article.getUniteBase())
                    .stock(stock)
                    .reserve(reserve)
                    .disponible(disponible.max(BigDecimal.ZERO))
                    .enCommande(attendu)
                    .seuil(seuil)
                    .vendu(vendu)
                    .parJour(parJour.setScale(2, RoundingMode.HALF_UP))
                    .couvertureJours(parJour.signum() == 0 ? null
                            : disponible.max(BigDecimal.ZERO).divide(parJour, 0, RoundingMode.DOWN))
                    .besoin(besoin.setScale(2, RoundingMode.UP))
                    .fournisseur(achat == null ? null : FournisseurDto.fromEntity(achat.getCommandeFournisseur().getFournisseur()))
                    .conditionnement(unite == null ? null : ConditionnementDto.fromEntity(unite))
                    .contenance(contenance)
                    .quantiteProposee(quantite)
                    .prixAchat(prix)
                    .raison(disponible.signum() <= 0 ? "RUPTURE"
                            : seuil != null && disponible.compareTo(seuil) <= 0 ? "SOUS_SEUIL" : "A_COUVRIR")
                    .build());
        }
        // Le plus urgent en tete : ce qui manque deja, puis ce qui tient le moins de jours.
        lignes.sort(Comparator.comparing((ReapproDto.LigneReappro l) -> !"RUPTURE".equals(l.getRaison()))
                .thenComparing(ReapproDto.LigneReappro::getCouvertureJours, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ReapproDto.LigneReappro::getDesignation, Comparator.nullsLast(Comparator.naturalOrder())));

        return ReapproDto.builder()
                .idSite(site.getId())
                .nomSite(site.getNom())
                .joursCouverture(couverture)
                .joursObserves(JOURS_OBSERVES)
                .lignes(lignes)
                .build();
    }

    @Override
    @Transactional
    public List<CommandeFourDto> creerLesCommandes(CommandesReapproDto commandes) {
        List<CommandesReapproDto.LigneCommandeReappro> retenues = commandes == null || commandes.lignes() == null ? List.of()
                : commandes.lignes().stream().filter(l -> l.quantite() != null && l.quantite().signum() > 0).toList();
        if (retenues.isEmpty()) {
            throw new InvalidEntityException("Rien à commander : aucune ligne n'a de quantité",
                    ErrorCodes.COMMANDE_FOURNISSEUR_NOT_VALID);
        }
        List<String> sansFournisseur = retenues.stream().filter(l -> l.idFournisseur() == null)
                .map(l -> "Article " + l.idArticle() + " : choisissez son fournisseur").toList();
        if (!sansFournisseur.isEmpty()) {
            throw new InvalidEntityException("Chaque ligne retenue a besoin d'un fournisseur",
                    ErrorCodes.COMMANDE_FOURNISSEUR_NOT_VALID, sansFournisseur);
        }
        Site site = siteCourant.site();

        Map<Long, List<CommandesReapproDto.LigneCommandeReappro>> parFournisseur = retenues.stream()
                .collect(Collectors.groupingBy(CommandesReapproDto.LigneCommandeReappro::idFournisseur, LinkedHashMap::new,
                        Collectors.toList()));
        List<CommandeFourDto> creees = new ArrayList<>();
        String jour = JOUR.format(LocalDate.now());
        // La commande passe par le meme chemin qu'une commande saisie a la main : memes controles
        // d'article, de conditionnement et de site.
        parFournisseur.forEach((idFournisseur, lignes) -> creees.add(commandeFourService.save(CommandeFourDto.builder()
                .code("REA-" + jour + "-" + Integer.toString(ThreadLocalRandom.current().nextInt(36 * 36 * 36), 36).toUpperCase())
                .dateCommande(Instant.now())
                .fournisseur(FournisseurDto.builder().id(idFournisseur).build())
                .idSite(site == null ? null : site.getId())
                .ligneCmndeFournisseur(lignes.stream().map(l -> LigneCmndeFournisseurDto.builder()
                        .article(ArticleDto.builder().Id(l.idArticle()).build())
                        .conditionnement(l.idConditionnement() == null ? null
                                : ConditionnementDto.builder().id(l.idConditionnement()).build())
                        .quantite(l.quantite())
                        .prixUnitaire(l.prixUnitaire())
                        .build()).toList())
                .build())));
        log.info("Reapprovisionnement : {} commande(s) en preparation pour {} ligne(s)", creees.size(), retenues.size());
        return creees;
    }

    private static BigDecimal arrondir(BigDecimal quantite, boolean entier) {
        return entier ? quantite.setScale(0, RoundingMode.CEILING) : quantite.setScale(2, RoundingMode.CEILING);
    }

    private static Map<Long, BigDecimal> parArticle(List<Object[]> lignes) {
        Map<Long, BigDecimal> resultat = new HashMap<>();
        for (Object[] ligne : lignes) {
            resultat.put((Long) ligne[0], (BigDecimal) ligne[1]);
        }
        return resultat;
    }
}
