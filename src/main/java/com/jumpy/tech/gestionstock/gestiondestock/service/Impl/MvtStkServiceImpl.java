package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.MvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.MvtStkRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.MvtStkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MvtStkServiceImpl implements MvtStkService {

    private final MvtStkRepository mvtStkRepository;
    private final ArticleRepository articleRepository;

    public MvtStkServiceImpl(MvtStkRepository mvtStkRepository, ArticleRepository articleRepository) {
        this.mvtStkRepository = mvtStkRepository;
        this.articleRepository = articleRepository;
    }

    @Override
    public BigDecimal stockReelArticle(Long idArticle) {
        Article article = article(idArticle);
        return stockReel(article.getId());
    }

    @Override
    public List<MvtStkDto> mvtStkArticle(Long idArticle) {
        article(idArticle);
        return mvtStkRepository.findAllByArticlesIdOrderByDateMvtDesc(idArticle).stream()
                .map(MvtStkDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MvtStkDto entreeStock(MvtStkDto dto) {
        return enregistrer(dto, TypeMvtStk.ENTREE);
    }

    @Override
    @Transactional
    public MvtStkDto sortieStock(MvtStkDto dto) {
        return enregistrer(dto, TypeMvtStk.SORTIE);
    }

    /**
     * Le sens du mouvement vient de la methode appelee, jamais du DTO : un client qui poste une
     * sortie en la marquant « entree » augmenterait le stock au lieu de le diminuer.
     */
    private MvtStkDto enregistrer(MvtStkDto dto, TypeMvtStk sens) {
        if (dto == null || dto.getArticle() == null || dto.getArticle().getId() == null) {
            throw new InvalidEntityException("Un mouvement de stock designe un article",
                    ErrorCodes.MVT_STK_NOT_VALID);
        }
        Article article = article(dto.getArticle().getId());
        BigDecimal quantite = quantiteValide(dto.getQuantite());

        if (sens == TypeMvtStk.SORTIE) {
            verifierStockDisponible(article, quantite);
        }

        MvtStk mvtStk = new MvtStk();
        mvtStk.setArticles(article);
        mvtStk.setQuantite(quantite);
        mvtStk.setTypMvt(sens);
        // Un mouvement est date du moment ou il a lieu. Laisser le client fournir la date
        // permettrait d'antidater une sortie, et donc de fabriquer un stock qui n'a jamais existe.
        mvtStk.setDateMvt(Instant.now());
        mvtStk.setIdEntreprise(dto.getIdEntreprise());

        MvtStk enregistre = mvtStkRepository.save(mvtStk);
        log.info("Mouvement {} de {} sur l'article {}", sens, quantite, article.getId());
        return MvtStkDto.fromEntity(enregistre);
    }

    /**
     * Quantite toujours positive : c'est le sens du mouvement qui porte le signe. Une sortie de
     * -5 enregistree telle quelle augmenterait le stock, par la seule vertu de deux signes qui
     * s'annulent.
     */
    private BigDecimal quantiteValide(BigDecimal quantite) {
        if (quantite == null || quantite.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidEntityException(
                    "La quantite d'un mouvement de stock doit etre strictement positive",
                    ErrorCodes.MVT_STK_NOT_VALID);
        }
        return quantite;
    }

    private void verifierStockDisponible(Article article, BigDecimal quantite) {
        BigDecimal disponible = stockReel(article.getId());
        if (disponible.compareTo(quantite) < 0) {
            throw new InvalidEntityException(
                    "Stock insuffisant pour l'article " + article.getCodeArticle()
                            + " : " + disponible + " en magasin, " + quantite + " demandes",
                    ErrorCodes.STOCK_INSUFFISANT,
                    List.of("Article " + article.getCodeArticle() + " : stock reel " + disponible
                            + ", quantite demandee " + quantite));
        }
    }

    private BigDecimal stockReel(Long idArticle) {
        BigDecimal entrees = mvtStkRepository.sommeParType(idArticle, TypeMvtStk.ENTREE);
        BigDecimal sorties = mvtStkRepository.sommeParType(idArticle, TypeMvtStk.SORTIE);
        return entrees.subtract(sorties);
    }

    private Article article(Long idArticle) {
        if (idArticle == null) {
            throw new InvalidEntityException("Aucun Article ne peut etre cherche sans identifiant",
                    ErrorCodes.ARTICLE_NOT_VALID);
        }
        return articleRepository.findById(idArticle)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun Article avec l'id = " + idArticle + " n'a ete trouve dans la base de donnees",
                        ErrorCodes.ARTICLE_NOT_FOUND));
    }
}
