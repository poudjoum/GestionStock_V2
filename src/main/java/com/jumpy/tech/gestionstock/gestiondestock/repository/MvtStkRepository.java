package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.MvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeMvtStk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface MvtStkRepository extends JpaRepository<MvtStk, Long> {

    List<MvtStk> findAllByArticlesIdOrderByDateMvtDesc(Long idArticle);

    /**
     * Somme des quantites d'un article pour un sens de mouvement donne.
     *
     * Le stock reel se deduit de cette somme prise deux fois — entrees moins sorties — plutot que
     * d'une colonne « quantite en stock » tenue a jour sur l'article : une colonne se desynchronise
     * au premier traitement interrompu, une somme de mouvements non. Le jour ou cette lecture
     * coutera trop cher, ce sera un cache a ajouter, pas une seconde source de verite.
     *
     * `coalesce` parce qu'un article sans aucun mouvement vaut zero, et non null.
     */
    @Query("select coalesce(sum(m.quantite), 0) from MvtStk m " +
            "where m.articles.id = :idArticle and m.typMvt = :typeMvt")
    BigDecimal sommeParType(@Param("idArticle") Long idArticle, @Param("typeMvt") TypeMvtStk typeMvt);
}
