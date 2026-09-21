package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article,Long> {


    Optional<Article> findArticleByCodeArticle(String codeArticle);

    // Les variantes cloisonnees. Spring Data traduit `IdEntreprise` en `id_entreprise`, et
    // `findAllByIdEntreprise(null)` rend bien les lignes ou la colonne est nulle — c'est ce que
    // voit un compte sans entreprise.
    List<Article> findAllByIdEntreprise(Long idEntreprise);

    Page<Article> findAllByIdEntreprise(Long idEntreprise, Pageable pageable);

    Optional<Article> findArticleByCodeArticleAndIdEntreprise(String codeArticle, Long idEntreprise);
}
