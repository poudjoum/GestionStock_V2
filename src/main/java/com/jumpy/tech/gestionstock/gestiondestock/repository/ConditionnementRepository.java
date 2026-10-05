package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Conditionnement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ConditionnementRepository extends JpaRepository<Conditionnement, Long> {

    List<Conditionnement> findAllByArticleIdOrderByQuantiteUnitesAsc(Long idArticle);

    /** Les conditionnements de toute une page d'articles, en une requete plutot qu'une par article. */
    List<Conditionnement> findAllByArticleIdInOrderByQuantiteUnitesAsc(Collection<Long> idsArticles);
}
