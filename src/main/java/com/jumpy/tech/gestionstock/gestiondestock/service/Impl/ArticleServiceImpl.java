package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.ArticleService;
import com.jumpy.tech.gestionstock.gestiondestock.validator.ArticleValidators;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ArticleServiceImpl implements ArticleService {
    private ArticleRepository articleRepository;

    public ArticleServiceImpl(ArticleRepository articleRepository){
        this.articleRepository=articleRepository;
    }
    @Override
    @Transactional
    public ArticleDto save(ArticleDto dto) {
        List<String>errors= ArticleValidators.validate(dto);
        if(!errors.isEmpty()){
            log.error("Article not valid {}",dto);
            throw new InvalidEntityException("Article is not valid", ErrorCodes.ARTICLE_NOT_VALID,errors);
        }
        Article savedArticle=articleRepository.save(ArticleDto.toEntity(dto));
        return ArticleDto.fromEntity(savedArticle);
    }

    @Override
    public ArticleDto findById(Long id) {
        if(id==null){
            log.error("Article id is null");
            throw new InvalidEntityException("Aucun Article ne peut etre cherche sans identifiant",
                    ErrorCodes.ARTICLE_NOT_VALID);
        }
        // `article.get()` precedait le orElseThrow : sur un identifiant inconnu, c'est
        // NoSuchElementException qui partait — une erreur 500 — et le orElseThrow, applique a un
        // Optional.of() toujours plein, ne pouvait jamais lever le 404 qu'il decrivait.
        return articleRepository.findById(id)
                .map(ArticleDto::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun Article avec l'id = " + id + " n'a ete trouve dans la base de donnees",
                        ErrorCodes.ARTICLE_NOT_FOUND));
    }

    @Override
    public ArticleDto findByCodeArticle(String codeArticle) {
        if(!StringUtils.hasLength(codeArticle)){
            log.error("Le code Article est vide");
            throw new InvalidEntityException("Aucun Article ne peut etre cherche sans code",
                    ErrorCodes.ARTICLE_NOT_VALID);
        }
        return articleRepository.findArticleByCodeArticle(codeArticle)
                .map(ArticleDto::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun Article avec le code = " + codeArticle + " n'a ete trouve dans la base de donnees",
                        ErrorCodes.ARTICLE_NOT_FOUND));
    }

    @Override
    public List<ArticleDto> findAll() {
        return articleRepository.findAll().stream()
                .map(ArticleDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if(id==null){
            log.error("Article Id est null");
            return;
        }
        articleRepository.deleteById(id);
    }
}
