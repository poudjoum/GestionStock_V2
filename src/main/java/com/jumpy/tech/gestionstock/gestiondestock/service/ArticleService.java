package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ArticleService {

    ArticleDto save(ArticleDto dto);
    ArticleDto findById(Long id);
    ArticleDto findByCodeArticle(String codeArticle);
    List<ArticleDto> findAll();

    /**
     * Le catalogue par tranches. `findAll()` charge tout le stock en memoire et le serialise d'un
     * bloc : passable sur les trente articles d'aujourd'hui, intenable sur les dix mille de
     * demain. Les deux coexistent, le temps que les appelants basculent.
     */
    /**
     * Liste paginee, filtrable par texte libre et par categorie.
     *
     * `q` porte sur le code et la designation ; les deux filtres sont facultatifs. Sans eux la
     * liste est entiere — une seule route plutot qu'une pour lister et une pour chercher.
     */
    Page<ArticleDto> findAll(String q, Long idCategory, Pageable pageable);

    void delete(Long id);
}
