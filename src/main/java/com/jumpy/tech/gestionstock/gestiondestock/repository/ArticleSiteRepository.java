package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.ArticleSite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ArticleSiteRepository extends JpaRepository<ArticleSite, Long> {

    Optional<ArticleSite> findByArticleIdAndSiteId(Long idArticle, Long idSite);

    List<ArticleSite> findAllByArticleId(Long idArticle);

    /** Les seuils d'une page d'articles dans un site, en une requete. */
    List<ArticleSite> findAllBySiteIdAndArticleIdIn(Long idSite, Collection<Long> idsArticles);
}
