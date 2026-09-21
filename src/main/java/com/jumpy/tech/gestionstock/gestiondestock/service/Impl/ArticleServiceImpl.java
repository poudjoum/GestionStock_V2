package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.ArticleService;
import com.jumpy.tech.gestionstock.gestiondestock.validator.ArticleValidators;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ArticleServiceImpl implements ArticleService {
    private ArticleRepository articleRepository;
    private final Cloisonnement cloisonnement;

    public ArticleServiceImpl(ArticleRepository articleRepository, Cloisonnement cloisonnement){
        this.articleRepository=articleRepository;
        this.cloisonnement=cloisonnement;
    }
    @Override
    @Transactional
    public ArticleDto save(ArticleDto dto) {
        List<String>errors= ArticleValidators.validate(dto);
        if(!errors.isEmpty()){
            log.error("Article not valid {}",dto);
            throw new InvalidEntityException("L'article n'est pas valide", ErrorCodes.ARTICLE_NOT_VALID,errors);
        }
        Article article = ArticleDto.toEntity(dto);
        if (article.getId() != null) {
            // Modification : on verifie d'abord que l'article vise est bien le sien, sans quoi
            // connaitre un identifiant suffirait a reecrire le catalogue du voisin.
            Article existant = article(article.getId());
            article.setIdEntreprise(existant.getIdEntreprise());
        } else if (cloisonnement.filtre()) {
            // L'entreprise vient du compte, jamais du corps de la requete.
            article.setIdEntreprise(cloisonnement.entrepriseCourante());
        }
        return ArticleDto.fromEntity(articleRepository.save(article));
    }

    @Override
    public ArticleDto findById(Long id) {
        if(id==null){
            log.error("Article id is null");
            throw new InvalidEntityException("Aucun article ne peut être cherché sans identifiant",
                    ErrorCodes.ARTICLE_NOT_VALID);
        }
        // `article.get()` precedait le orElseThrow : sur un identifiant inconnu, c'est
        // NoSuchElementException qui partait — une erreur 500 — et le orElseThrow, applique a un
        // Optional.of() toujours plein, ne pouvait jamais lever le 404 qu'il decrivait.
        return ArticleDto.fromEntity(article(id));
    }

    /**
     * L'article, a condition qu'il appartienne a l'entreprise de l'appelant.
     *
     * Un article d'une autre entreprise rend un 404 et non un 403 : repondre « interdit »
     * confirmerait son existence, et permettrait de deviner le catalogue du voisin en essayant
     * des identifiants.
     */
    private Article article(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun article avec l'identifiant " + id + " n'a été trouvé",
                        ErrorCodes.ARTICLE_NOT_FOUND));
        cloisonnement.verifierAcces(article.getIdEntreprise(), "article", id);
        return article;
    }

    @Override
    public ArticleDto findByCodeArticle(String codeArticle) {
        if(!StringUtils.hasLength(codeArticle)){
            log.error("Le code Article est vide");
            throw new InvalidEntityException("Aucun article ne peut être cherché sans code",
                    ErrorCodes.ARTICLE_NOT_VALID);
        }
        // La recherche est cloisonnee des la requete : deux entreprises peuvent employer le meme
        // code d'article, et rien ne l'interdit.
        return (cloisonnement.filtre()
                ? articleRepository.findArticleByCodeArticleAndIdEntreprise(codeArticle, cloisonnement.entrepriseCourante())
                : articleRepository.findArticleByCodeArticle(codeArticle))
                .map(ArticleDto::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun article avec le code " + codeArticle + " n'a été trouvé",
                        ErrorCodes.ARTICLE_NOT_FOUND));
    }

    @Override
    public List<ArticleDto> findAll() {
        return (cloisonnement.filtre()
                ? articleRepository.findAllByIdEntreprise(cloisonnement.entrepriseCourante())
                : articleRepository.findAll()).stream()
                .map(ArticleDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public Page<ArticleDto> findAll(Pageable pageable) {
        // `map` sur la Page conserve le total et le numero de page : reconstruire une Page a la
        // main a partir du contenu ferait perdre ce que le client utilise pour naviguer.
        return (cloisonnement.filtre()
                ? articleRepository.findAllByIdEntreprise(cloisonnement.entrepriseCourante(), pageable)
                : articleRepository.findAll(pageable))
                .map(ArticleDto::fromEntity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if(id==null){
            log.error("Article Id est null");
            return;
        }
        // Passe par `article(id)` : on ne supprime pas ce qui n'est pas a soi.
        articleRepository.delete(article(id));
    }
}
