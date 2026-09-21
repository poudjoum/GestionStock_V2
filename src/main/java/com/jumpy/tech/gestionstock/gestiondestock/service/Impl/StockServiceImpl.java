package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EtatDuStockDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneInventaireDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;
import com.jumpy.tech.gestionstock.gestiondestock.entities.StatutStock;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
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

    public StockServiceImpl(ArticleRepository articleRepository, MvtStkRepository mvtStkRepository,
                            LigneCmndeFourRepository ligneCmndeFourRepository,
                            Cloisonnement cloisonnement) {
        this.articleRepository = articleRepository;
        this.mvtStkRepository = mvtStkRepository;
        this.ligneCmndeFourRepository = ligneCmndeFourRepository;
        this.cloisonnement = cloisonnement;
    }

    @Override
    public EtatDuStockDto etat() {
        List<LigneInventaireDto> lignes = inventaireDe(tousLesArticles());

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
        List<LigneInventaireDto> lignes = inventaireDe(page.getContent());
        Map<Long, LigneInventaireDto> parArticle = lignes.stream()
                .collect(Collectors.toMap(LigneInventaireDto::getIdArticle, ligne -> ligne));
        return page.map(article -> parArticle.get(article.getId()));
    }

    @Override
    public List<LigneInventaireDto> alertes() {
        return inventaireDe(tousLesArticles()).stream()
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

    private List<LigneInventaireDto> inventaireDe(List<Article> articles) {
        if (articles.isEmpty()) {
            return List.of();
        }
        List<Long> ids = articles.stream().map(Article::getId).collect(Collectors.toList());
        Map<Long, BigDecimal> stocks = stocks(ids);
        Map<Long, BigDecimal> couts = coutsMoyens(ids);

        return articles.stream()
                .map(article -> ligne(article, stocks.getOrDefault(article.getId(), BigDecimal.ZERO),
                        couts.get(article.getId())))
                .collect(Collectors.toList());
    }

    private LigneInventaireDto ligne(Article article, BigDecimal quantite, BigDecimal coutMoyen) {
        BigDecimal prixVente = article.getPrixUnitaire();
        return LigneInventaireDto.builder()
                .idArticle(article.getId())
                .codeArticle(article.getCodeArticle())
                .designation(article.getDesignation())
                .quantite(quantite)
                .seuilAlerte(article.getSeuilAlerte())
                .statut(statut(quantite, article.getSeuilAlerte()))
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

    private Map<Long, BigDecimal> stocks(List<Long> idsArticles) {
        Map<Long, BigDecimal> stocks = new HashMap<>();
        for (Object[] ligne : mvtStkRepository.stocksReels(idsArticles, TypeMvtStk.ENTREE)) {
            stocks.put((Long) ligne[0], (BigDecimal) ligne[1]);
        }
        return stocks;
    }

    /**
     * Le cout d'achat moyen : tout ce qui a ete achete, divise par tout ce qui est entre.
     *
     * Moyen et non « dernier prix connu » : le dernier achat peut etre une petite quantite a un
     * prix exceptionnel, et valoriser tout le stock a ce prix-la donnerait un chiffre faux.
     */
    private Map<Long, BigDecimal> coutsMoyens(List<Long> idsArticles) {
        Map<Long, BigDecimal> couts = new HashMap<>();
        for (Object[] ligne : ligneCmndeFourRepository.coutsAchetes(idsArticles, EtatCommande.LIVREE)) {
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
