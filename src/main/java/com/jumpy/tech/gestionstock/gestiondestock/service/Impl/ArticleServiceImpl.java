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
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ArticleServiceImpl implements ArticleService {
    private ArticleRepository articleRepository;

    public ArticleServiceImpl(ArticleRepository articleRepository){
        this.articleRepository=articleRepository;
    }
    @Override
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
        }
        Optional<Article> article=articleRepository.findById(id);
        ArticleDto dto=ArticleDto.fromEntity(article.get());
        return Optional.of(dto).orElseThrow(()->
                new EntityNotFoundException("Aucun Article avec l'id= "+id+" n'a été trouvé dans la base de donnée",ErrorCodes.ARTICLE_NOT_FOUND));
    }

    @Override
    public ArticleDto findByCodeArticle(String codeArticle) {
        if(!StringUtils.hasLength(codeArticle)){
            log.error("Le code Article est vide");
            return null;
        }
        Optional<Article> article= articleRepository.findArticleByCodeArticle(codeArticle);
        ArticleDto dto= ArticleDto.fromEntity(article.get());
        return Optional.of(dto).orElseThrow(()->
                new EntityNotFoundException("Aucun Article avec le code = "+codeArticle +"n'a été trouvé dans la base de donnée",ErrorCodes.ARTICLE_NOT_FOUND));
    }

    @Override
    public List<ArticleDto> findAll() {
        return articleRepository.findAll().stream()
                .map(ArticleDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        if(id==null){
            log.error("Article Id est null");
            return;
        }
        articleRepository.deleteById(id);
    }
}
