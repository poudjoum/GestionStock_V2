package com.jumpy.tech.gestionstock.gestiondestock.analyse;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Site;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneCmndeFourRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneVenteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.MvtStkRepository;
import com.jumpy.tech.gestionstock.gestiondestock.site.SiteCourant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Les analyses du catalogue : classes ABC, couverture, dormants. */
@Service
@Transactional(readOnly = true)
public class AnalyseService {

    private static final BigDecimal CENT = BigDecimal.valueOf(100);
    private static final BigDecimal SEUIL_A = BigDecimal.valueOf(80);
    private static final BigDecimal SEUIL_B = BigDecimal.valueOf(95);

    private final ArticleRepository articleRepository;
    private final LigneVenteRepository ligneVenteRepository;
    private final MvtStkRepository mvtStkRepository;
    private final LigneCmndeFourRepository ligneCmndeFourRepository;
    private final SiteCourant siteCourant;
    private final Cloisonnement cloisonnement;

    public AnalyseService(ArticleRepository articleRepository, LigneVenteRepository ligneVenteRepository,
                          MvtStkRepository mvtStkRepository, LigneCmndeFourRepository ligneCmndeFourRepository,
                          SiteCourant siteCourant, Cloisonnement cloisonnement) {
        this.articleRepository = articleRepository;
        this.ligneVenteRepository = ligneVenteRepository;
        this.mvtStkRepository = mvtStkRepository;
        this.ligneCmndeFourRepository = ligneCmndeFourRepository;
        this.siteCourant = siteCourant;
        this.cloisonnement = cloisonnement;
    }

    public AnalyseArticlesDto articles(int jours, boolean tousSites) {
        if (jours < 7 || jours > 365) {
            throw new InvalidEntityException("La période d'analyse va de 7 à 365 jours", ErrorCodes.ARTICLE_NOT_VALID);
        }
        Long idEntreprise = cloisonnement.entrepriseCourante();
        if (idEntreprise == null) {
            return AnalyseArticlesDto.builder().jours(jours).chiffreAffaires(BigDecimal.ZERO)
                    .valeurDormante(BigDecimal.ZERO).articles(List.of()).build();
        }
        Site site = tousSites && siteCourant.voitTousLesSites() ? null : siteCourant.site();

        List<Article> articles = articleRepository.findAllByIdEntreprise(idEntreprise);
        List<Long> ids = articles.stream().map(Article::getId).toList();
        Map<Long, Object[]> ventes = new HashMap<>();
        for (Object[] ligne : ligneVenteRepository.ventesParArticle(idEntreprise, site == null ? null : site.getId(),
                Instant.now().minus(jours, ChronoUnit.DAYS))) {
            ventes.put((Long) ligne[0], ligne);
        }
        Map<Long, BigDecimal> stocks = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] ligne : site == null
                    ? mvtStkRepository.stocksReels(ids, TypeMvtStk.ENTREE)
                    : mvtStkRepository.stocksReelsDansSite(ids, site.getId(), TypeMvtStk.ENTREE)) {
                stocks.put((Long) ligne[0], (BigDecimal) ligne[1]);
            }
        }
        Map<Long, BigDecimal> couts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] ligne : ligneCmndeFourRepository.coutsAchetes(ids)) {
                BigDecimal quantite = (BigDecimal) ligne[2];
                if (quantite != null && quantite.signum() > 0) {
                    couts.put((Long) ligne[0], ((BigDecimal) ligne[1]).divide(quantite, 4, RoundingMode.HALF_UP));
                }
            }
        }

        BigDecimal total = ventes.values().stream().map(v -> (BigDecimal) v[1]).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal periode = BigDecimal.valueOf(jours);
        List<AnalyseArticlesDto.LigneAnalyse> lignes = new ArrayList<>();
        for (Article article : articles) {
            Object[] vente = ventes.get(article.getId());
            BigDecimal ca = vente == null ? BigDecimal.ZERO : (BigDecimal) vente[1];
            BigDecimal vendu = vente == null ? BigDecimal.ZERO : (BigDecimal) vente[2];
            BigDecimal stock = stocks.getOrDefault(article.getId(), BigDecimal.ZERO);
            // Un article sans vente et sans stock n'a rien a dire : il encombrerait la liste.
            if (ca.signum() == 0 && vendu.signum() == 0 && stock.signum() == 0) {
                continue;
            }
            BigDecimal cout = couts.get(article.getId());
            lignes.add(AnalyseArticlesDto.LigneAnalyse.builder()
                    .idArticle(article.getId())
                    .codeArticle(article.getCodeArticle())
                    .designation(article.getDesignation())
                    .uniteBase(article.getUniteBase())
                    .chiffreAffaires(ca.setScale(0, RoundingMode.HALF_UP))
                    .quantiteVendue(vendu)
                    .stock(stock)
                    .couvertureJours(vendu.signum() <= 0 ? null
                            : stock.max(BigDecimal.ZERO).multiply(periode).divide(vendu, 0, RoundingMode.DOWN))
                    .dormant(stock.signum() > 0 && vendu.signum() <= 0)
                    .valeurStock(cout == null || stock.signum() <= 0 ? null
                            : stock.multiply(cout).setScale(0, RoundingMode.HALF_UP))
                    .derniereVente(vente == null ? null : (Instant) vente[3])
                    .build());
        }

        // Le plus gros chiffre d'affaires en tete, et les classes se lisent sur la part cumulee :
        // un article est A tant que ce qui le precede n'a pas deja fait 80 %.
        lignes.sort(Comparator.comparing(AnalyseArticlesDto.LigneAnalyse::getChiffreAffaires).reversed()
                .thenComparing(AnalyseArticlesDto.LigneAnalyse::getDesignation, Comparator.nullsLast(Comparator.naturalOrder())));
        BigDecimal cumul = BigDecimal.ZERO;
        long a = 0, b = 0, c = 0, dormants = 0;
        BigDecimal valeurDormante = BigDecimal.ZERO;
        for (AnalyseArticlesDto.LigneAnalyse ligne : lignes) {
            BigDecimal avant = total.signum() == 0 ? BigDecimal.ZERO
                    : cumul.multiply(CENT).divide(total, 2, RoundingMode.HALF_UP);
            cumul = cumul.add(ligne.getChiffreAffaires());
            BigDecimal part = total.signum() == 0 ? BigDecimal.ZERO
                    : ligne.getChiffreAffaires().multiply(CENT).divide(total, 2, RoundingMode.HALF_UP);
            ligne.setPart(part);
            ligne.setPartCumulee(total.signum() == 0 ? BigDecimal.ZERO
                    : cumul.multiply(CENT).divide(total, 2, RoundingMode.HALF_UP));
            String classe = ligne.getChiffreAffaires().signum() <= 0 ? "C"
                    : avant.compareTo(SEUIL_A) < 0 ? "A" : avant.compareTo(SEUIL_B) < 0 ? "B" : "C";
            ligne.setClasse(classe);
            switch (classe) {
                case "A" -> a++;
                case "B" -> b++;
                default -> c++;
            }
            if (ligne.isDormant()) {
                dormants++;
                if (ligne.getValeurStock() != null) {
                    valeurDormante = valeurDormante.add(ligne.getValeurStock());
                }
            }
        }

        return AnalyseArticlesDto.builder()
                .jours(jours)
                .idSite(site == null ? null : site.getId())
                .nomSite(site == null ? null : site.getNom())
                .chiffreAffaires(total.setScale(0, RoundingMode.HALF_UP))
                .nombreA(a).nombreB(b).nombreC(c)
                .nombreDormants(dormants)
                .valeurDormante(valeurDormante)
                .articles(lignes)
                .build();
    }
}
