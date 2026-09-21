package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * La recherche du comptoir : un bout de code ou de designation, et la categorie.
     *
     * Les listes etaient paginees mais pas filtrables : `/articles?page=3` rendait la page 3 de
     * tout le catalogue. Un caissier qui tape « cim » pour trouver « Sac de ciment » n'avait rien,
     * et sur un telephone c'est la difference entre utilisable et inutilisable.
     *
     * `q` et `idCategory` sont facultatifs : absents, la requete rend la liste entiere, ce qui
     * evite d'avoir deux routes pour la meme chose. `q` vaut la chaine vide et non `null` quand
     * il ne filtre rien — voir RechercheUtils : un nul sans type fait choisir `lower(bytea)` a
     * PostgreSQL, et la requete echoue.
     *
     * L'entreprise se compare a l'identique, nul compris — un compte sans entreprise voit les
     * donnees qui n'en ont pas, et `= null` n'est jamais vrai en SQL.
     */
    @Query("""
            select a from Article a
            where (:filtrer = false
                   or a.idEntreprise = :idEntreprise
                   or (:idEntreprise is null and a.idEntreprise is null))
              and (:q = ''
                   or lower(a.codeArticle) like lower(concat('%', :q, '%'))
                   or lower(a.designation) like lower(concat('%', :q, '%')))
              and (:idCategory is null or a.category.id = :idCategory)
            """)
    Page<Article> rechercher(@Param("filtrer") boolean filtrer,
                             @Param("idEntreprise") Long idEntreprise,
                             @Param("q") String q,
                             @Param("idCategory") Long idCategory,
                             Pageable pageable);
}
