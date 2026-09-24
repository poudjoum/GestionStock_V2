package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneComptage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LigneComptageRepository extends JpaRepository<LigneComptage, Long> {

    List<LigneComptage> findAllBySeanceId(Long idSeance);

    Optional<LigneComptage> findBySeanceIdAndArticleId(Long idSeance, Long idArticle);

    long countBySeanceIdAndQuantiteCompteeIsNull(Long idSeance);

    /**
     * Les lignes d'une seance, filtrees par ce qu'on cherche et par ce qu'on veut voir.
     *
     * Trois vues, et elles ne se recouvrent pas : tout, ce qui reste a compter, et les ecarts. La
     * derniere est celle qu'on regarde avant de valider — on ne relit pas mille lignes justes pour
     * trouver les douze qui ne le sont pas.
     *
     * L'ecart se calcule ici plutot qu'en memoire : les lignes d'un magasin se comptent en
     * milliers, et les rapatrier toutes pour n'en garder que quelques-unes ferait passer la page a
     * la seconde.
     */
    @Query("""
            select l from LigneComptage l
            where l.seance.id = :idSeance
              and (:q = '' or lower(l.designation) like lower(concat('%', :q, '%'))
                           or lower(l.codeArticle) like lower(concat('%', :q, '%')))
              and (:vue = 'TOUTES'
                   or (:vue = 'A_COMPTER' and l.quantiteComptee is null)
                   or (:vue = 'ECARTS' and l.quantiteComptee is not null
                       and l.quantiteComptee <> l.quantiteTheorique))
            """)
    Page<LigneComptage> rechercher(@Param("idSeance") Long idSeance,
                                   @Param("q") String q,
                                   @Param("vue") String vue,
                                   Pageable pageable);

    /** Les lignes comptees dont le stock du logiciel ne disait pas la meme chose que l'etagere. */
    @Query("""
            select l from LigneComptage l
            where l.seance.id = :idSeance
              and l.quantiteComptee is not null
              and l.quantiteComptee <> l.stockAuComptage
            """)
    List<LigneComptage> aCorriger(@Param("idSeance") Long idSeance);
}
