package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ConditionnementDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EtatDuStockDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneInventaireDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ArticleSite;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Conditionnement;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Site;
import com.jumpy.tech.gestionstock.gestiondestock.dto.StockSiteDto;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleSiteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.SiteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.site.SiteCourant;
import com.jumpy.tech.gestionstock.gestiondestock.entities.StatutStock;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ConditionnementRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneCmndeFourRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.MvtStkRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.StockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StockServiceImpl implements StockService {

    /** Deux decimales, comme partout ailleurs ou l'on manipule de l'argent. */
    private static final int DECIMALES = 2;
    private static final RoundingMode ARRONDI = RoundingMode.HALF_UP;

    private final ArticleRepository articleRepository;
    private final MvtStkRepository mvtStkRepository;
    private final LigneCmndeFourRepository ligneCmndeFourRepository;
    private final Cloisonnement cloisonnement;
    private final ConditionnementRepository conditionnementRepository;
    private final SiteCourant siteCourant;
    private final SiteRepository siteRepository;
    private final ArticleSiteRepository articleSiteRepository;

    public StockServiceImpl(ArticleRepository articleRepository, MvtStkRepository mvtStkRepository,
                            LigneCmndeFourRepository ligneCmndeFourRepository,
                            Cloisonnement cloisonnement,
                            ConditionnementRepository conditionnementRepository,
                            SiteCourant siteCourant, SiteRepository siteRepository,
                            ArticleSiteRepository articleSiteRepository) {
        this.siteCourant = siteCourant;
        this.siteRepository = siteRepository;
        this.articleSiteRepository = articleSiteRepository;
        this.conditionnementRepository = conditionnementRepository;
        this.articleRepository = articleRepository;
        this.mvtStkRepository = mvtStkRepository;
        this.ligneCmndeFourRepository = ligneCmndeFourRepository;
        this.cloisonnement = cloisonnement;
    }

    @Override
    public EtatDuStockDto etat() {
        return etat(false);
    }

    @Override
    public EtatDuStockDto etat(boolean tousSites) {
        List<LigneInventaireDto> lignes = inventaireDe(tousLesArticles(), portee(tousSites), tousSites);

        BigDecimal valeurAuCout = BigDecimal.ZERO;
        BigDecimal valeurVente = BigDecimal.ZERO;
        long sansCout = 0;
        long enRupture = 0;
        long sousSeuil = 0;

        for (LigneInventaireDto ligne : lignes) {
            if (ligne.getValeurAuCout() == null) {
                sansCout++;
            } else {
                valeurAuCout = valeurAuCout.add(ligne.getValeurAuCout());
            }
            if (ligne.getValeurAuPrixDeVente() != null) {
                valeurVente = valeurVente.add(ligne.getValeurAuPrixDeVente());
            }
            if (ligne.getStatut() == StatutStock.RUPTURE) {
                enRupture++;
            } else if (ligne.getStatut() == StatutStock.SOUS_SEUIL) {
                sousSeuil++;
            }
        }

        return EtatDuStockDto.builder()
                .nombreArticles(lignes.size())
                .nombreEnRupture(enRupture)
                .nombreSousSeuil(sousSeuil)
                .valeurAuCout(arrondi(valeurAuCout))
                .nombreSansCoutConnu(sansCout)
                .valeurAuPrixDeVente(arrondi(valeurVente))
                .build();
    }

    @Override
    public Page<LigneInventaireDto> inventaire(String q, Pageable pageable) {
        return inventaire(q, pageable, false);
    }

    @Override
    public Page<LigneInventaireDto> inventaire(String q, Pageable pageable, boolean tousSites) {
        // La meme recherche que sur le catalogue, et pour la meme raison : un magasinier debout
        // dans les rayons cherche un article, il ne feuillette pas l'inventaire page par page.
        Page<Article> page = articleRepository.rechercher(
                cloisonnement.filtre(),
                cloisonnement.filtre() ? cloisonnement.entrepriseCourante() : null,
                RechercheUtils.normaliser(q),
                null,
                pageable);

        // Les quantites et les couts de toute la page en deux requetes, quelle que soit sa
        // taille : les demander article par article en ferait deux par ligne affichee.
        List<LigneInventaireDto> lignes = inventaireDe(page.getContent(), portee(tousSites), tousSites);
        Map<Long, LigneInventaireDto> parArticle = lignes.stream()
                .collect(Collectors.toMap(LigneInventaireDto::getIdArticle, ligne -> ligne));
        return page.map(article -> parArticle.get(article.getId()));
    }

    @Override
    public List<LigneInventaireDto> alertes() {
        return alertes(false);
    }

    @Override
    public List<LigneInventaireDto> alertes(boolean tousSites) {
        if (tousSites && siteCourant.voitTousLesSites() && cloisonnement.entrepriseCourante() != null) {
            // Site par site : un magasin en rupture ne se rattrape pas par le stock de l'entrepot
            // sans que quelqu'un l'y transfere — il faut donc savoir lequel manque.
            List<Article> articles = tousLesArticles();
            return siteRepository.findAllByIdEntrepriseOrderByPrincipalDescNomAsc(cloisonnement.entrepriseCourante())
                    .stream()
                    .filter(Site::isActif)
                    .flatMap(site -> enDifficulte(inventaireDe(articles, site, false)).stream())
                    .collect(Collectors.toList());
        }
        return enDifficulte(inventaireDe(tousLesArticles(), portee(false), false));
    }

    private List<LigneInventaireDto> enDifficulte(List<LigneInventaireDto> lignes) {
        return lignes.stream()
                // Le negatif figure en tete : il n'attend pas une commande mais un comptage, et
                // il est vrai quel que soit le seuil — un article non surveille peut y tomber.
                .filter(ligne -> ligne.getStatut() == StatutStock.NEGATIF
                        || ligne.getStatut() == StatutStock.RUPTURE
                        || ligne.getStatut() == StatutStock.SOUS_SEUIL)
                .collect(Collectors.toList());
    }

    private List<Article> tousLesArticles() {
        return cloisonnement.filtre()
                ? articleRepository.findAllByIdEntreprise(cloisonnement.entrepriseCourante())
                : articleRepository.findAll();
    }

    /**
     * Le site dont on lit le stock : nul pour l'entreprise entiere, si l'appelant la voit ;
     * sinon le site actif. Un caissier qui demande « tous sites » lit donc son magasin.
     */
    private Site portee(boolean tousSites) {
        if (tousSites && siteCourant.voitTousLesSites()) {
            return null;
        }
        return siteCourant.site();
    }

    private List<LigneInventaireDto> inventaireDe(List<Article> articles, Site site, boolean detail) {
        if (articles.isEmpty()) {
            return List.of();
        }
        List<Long> ids = articles.stream().map(Article::getId).collect(Collectors.toList());
        Map<Long, BigDecimal> stocks = stocks(ids, site);
        // Le seuil du site, a defaut celui de l'article.
        Map<Long, BigDecimal> seuils = site == null ? Map.of()
                : articleSiteRepository.findAllBySiteIdAndArticleIdIn(site.getId(), ids).stream()
                        .filter(l -> l.getSeuilAlerte() != null)
                        .collect(Collectors.toMap(l -> l.getArticle().getId(), ArticleSite::getSeuilAlerte));
        Map<Long, List<StockSiteDto>> parSite = detail ? repartition(ids) : Map.of();
        Map<Long, BigDecimal> couts = coutsMoyens(ids);
        Map<Long, List<ConditionnementDto>> conditionnements =
                conditionnementRepository.findAllByArticleIdInOrderByQuantiteUnitesAsc(ids).stream()
                        .filter(Conditionnement::isActif)
                        .map(ConditionnementDto::fromEntity)
                        .collect(Collectors.groupingBy(ConditionnementDto::getIdArticle));

        return articles.stream()
                .map(article -> {
                    LigneInventaireDto ligne = ligne(article, stocks.getOrDefault(article.getId(), BigDecimal.ZERO),
                            couts.get(article.getId()),
                            conditionnements.getOrDefault(article.getId(), List.of()),
                            seuils.getOrDefault(article.getId(), article.getSeuilAlerte()));
                    ligne.setIdSite(site == null ? null : site.getId());
                    ligne.setNomSite(site == null ? null : site.getNom());
                    if (detail) {
                        ligne.setParSite(parSite.getOrDefault(article.getId(), List.of()));
                    }
                    return ligne;
                })
                .collect(Collectors.toList());
    }

    /** Le stock de chaque article dans chaque site visible, sites vides compris. */
    private Map<Long, List<StockSiteDto>> repartition(List<Long> ids) {
        Long entreprise = cloisonnement.entrepriseCourante();
        if (entreprise == null) {
            return Map.of();
        }
        List<Site> sites = siteRepository.findAllByIdEntrepriseOrderByPrincipalDescNomAsc(entreprise).stream()
                .filter(Site::isActif)
                .filter(siteCourant::peutVoir)
                .collect(Collectors.toList());
        Map<Long, Map<Long, BigDecimal>> quantites = new HashMap<>();
        for (Object[] l : mvtStkRepository.stocksParSite(ids, TypeMvtStk.ENTREE)) {
            quantites.computeIfAbsent((Long) l[0], k -> new HashMap<>()).put((Long) l[1], (BigDecimal) l[2]);
        }
        Map<Long, List<StockSiteDto>> resultat = new HashMap<>();
        for (Long id : ids) {
            Map<Long, BigDecimal> article = quantites.getOrDefault(id, Map.of());
            resultat.put(id, sites.stream()
                    .map(s -> new StockSiteDto(s.getId(), s.getNom(), s.getType(),
                            article.getOrDefault(s.getId(), BigDecimal.ZERO)))
                    .collect(Collectors.toList()));
        }
        return resultat;
    }

    private LigneInventaireDto ligne(Article article, BigDecimal quantite, BigDecimal coutMoyen,
                                     List<ConditionnementDto> conditionnements, BigDecimal seuil) {
        BigDecimal prixVente = article.getPrixUnitaire();
        return LigneInventaireDto.builder()
                .idArticle(article.getId())
                .codeArticle(article.getCodeArticle())
                .designation(article.getDesignation())
                .quantite(quantite)
                .uniteBase(article.getUniteBase())
                .suiviLot(article.isSuiviLot())
                .typeDate(article.getTypeDate())
                .conditionnements(conditionnements)
                .seuilAlerte(seuil)
                .statut(statut(quantite, seuil))
                .coutMoyenAchat(coutMoyen)
                // Nul quand le cout est inconnu : une valeur inventee se melerait aux vraies sans
                // qu'on puisse ensuite les distinguer.
                .valeurAuCout(coutMoyen == null ? null : arrondi(quantite.multiply(coutMoyen)))
                .prixUnitaireVente(prixVente)
                .valeurAuPrixDeVente(prixVente == null ? null : arrondi(quantite.multiply(prixVente)))
                .build();
    }

    /**
     * Le statut au regard du seuil.
     *
     * Une quantite negative se distingue d'une rupture. Elle etait rangee avec elle tant que les
     * sorties etaient toutes refusees au-dela du stock ; une vente faite hors ligne et
     * synchronisee apres coup peut desormais la faire passer sous zero. Les deux n'appellent pas
     * le meme geste : la rupture se commande au fournisseur, le negatif se compte sur l'etagere.
     */
    private StatutStock statut(BigDecimal quantite, BigDecimal seuil) {
        if (quantite.signum() < 0) {
            return StatutStock.NEGATIF;
        }
        if (quantite.signum() == 0) {
            return StatutStock.RUPTURE;
        }
        if (seuil == null) {
            return StatutStock.SANS_SEUIL;
        }
        return quantite.compareTo(seuil) <= 0 ? StatutStock.SOUS_SEUIL : StatutStock.SUFFISANT;
    }

    private Map<Long, BigDecimal> stocks(List<Long> idsArticles, Site site) {
        Map<Long, BigDecimal> stocks = new HashMap<>();
        List<Object[]> lignes = site == null
                ? mvtStkRepository.stocksReels(idsArticles, TypeMvtStk.ENTREE)
                : mvtStkRepository.stocksReelsDansSite(idsArticles, site.getId(), TypeMvtStk.ENTREE);
        for (Object[] ligne : lignes) {
            stocks.put((Long) ligne[0], (BigDecimal) ligne[1]);
        }
        return stocks;
    }

    /**
     * Le cout d'achat moyen : tout ce qui a ete achete, divise par tout ce qui est entre.
     *
     * Moyen et non « dernier prix connu » : le dernier achat peut etre une petite quantite a un
     * prix exceptionnel, et valoriser tout le stock a ce prix-la donnerait un chiffre faux.
     *
     * Le calcul suit ce qui est entre en magasin, livraison par livraison, et non les seules
     * commandes soldees : une reception partielle fait bien monter le stock, elle doit donc faire
     * monter sa valeur avec.
     */
    private Map<Long, BigDecimal> coutsMoyens(List<Long> idsArticles) {
        Map<Long, BigDecimal> couts = new HashMap<>();
        for (Object[] ligne : ligneCmndeFourRepository.coutsAchetes(idsArticles)) {
            BigDecimal montant = (BigDecimal) ligne[1];
            BigDecimal quantite = (BigDecimal) ligne[2];
            if (quantite != null && quantite.signum() > 0) {
                couts.put((Long) ligne[0], montant.divide(quantite, DECIMALES + 2, ARRONDI));
            }
        }
        return couts;
    }

    private BigDecimal arrondi(BigDecimal montant) {
        return montant.setScale(DECIMALES, ARRONDI);
    }
}
