package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.PromotionArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface PromotionArticleRepository extends JpaRepository<PromotionArticle, Long> {

    /** Les promotions d'un magasin qui valent ce jour-la. */
    @Query("""
            select p from PromotionArticle p join fetch p.campagne c join fetch p.article
            where p.idEntreprise = :idEntreprise and c.arretee = false
              and c.dateDebut <= :jour and c.dateFin >= :jour
            """)
    List<PromotionArticle> enCours(@Param("idEntreprise") Long idEntreprise, @Param("jour") LocalDate jour);

    /**
     * Les promotions d'autres campagnes qui chevauchent une periode, pour ces articles : un
     * article n'a qu'un prix promotionnel a la fois.
     */
    @Query("""
            select p from PromotionArticle p join fetch p.campagne c join fetch p.article a
            where p.idEntreprise = :idEntreprise and c.arretee = false
              and c.id <> :idCampagne
              and c.dateDebut <= :fin and c.dateFin >= :debut
              and a.id in :articles
            """)
    List<PromotionArticle> chevauchements(@Param("idEntreprise") Long idEntreprise,
                                          @Param("idCampagne") Long idCampagne,
                                          @Param("debut") LocalDate debut,
                                          @Param("fin") LocalDate fin,
                                          @Param("articles") Collection<Long> articles);
}
