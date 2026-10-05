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

    /**
     * Le stock de plusieurs articles en une requete.
     *
     * Un inventaire affiche des dizaines de lignes : les interroger une par une ferait deux
     * requetes par article — entrees puis sorties. Ici, une seule, et le signe est porte par le
     * sens du mouvement.
     *
     * Un article sans aucun mouvement n'est pas rendu : c'est a l'appelant de lire zero pour
     * ceux qui manquent, plutot qu'a la requete d'inventer des lignes vides.
     */
    @Query("select m.articles.id, coalesce(sum(case when m.typMvt = :entree then m.quantite " +
            "else -m.quantite end), 0) from MvtStk m " +
            "where m.articles.id in :idsArticles group by m.articles.id")
    java.util.List<Object[]> stocksReels(@Param("idsArticles") java.util.Collection<Long> idsArticles,
                                         @Param("entree") TypeMvtStk entree);

    /** La meme somme, dans un seul site : c'est le stock que le magasin a sous la main. */
    @Query("select coalesce(sum(m.quantite), 0) from MvtStk m " +
            "where m.articles.id = :idArticle and m.site.id = :idSite and m.typMvt = :typeMvt")
    BigDecimal sommeParTypeDansSite(@Param("idArticle") Long idArticle, @Param("idSite") Long idSite,
                                    @Param("typeMvt") TypeMvtStk typeMvt);

    /** Le stock de plusieurs articles dans un site, en une requete. */
    @Query("select m.articles.id, coalesce(sum(case when m.typMvt = :entree then m.quantite " +
            "else -m.quantite end), 0) from MvtStk m " +
            "where m.articles.id in :idsArticles and m.site.id = :idSite group by m.articles.id")
    java.util.List<Object[]> stocksReelsDansSite(@Param("idsArticles") java.util.Collection<Long> idsArticles,
                                                 @Param("idSite") Long idSite,
                                                 @Param("entree") TypeMvtStk entree);

    /** Le stock de plusieurs articles, site par site : article, site, quantite. */
    @Query("select m.articles.id, m.site.id, coalesce(sum(case when m.typMvt = :entree then m.quantite " +
            "else -m.quantite end), 0) from MvtStk m " +
            "where m.articles.id in :idsArticles and m.site is not null group by m.articles.id, m.site.id")
    java.util.List<Object[]> stocksParSite(@Param("idsArticles") java.util.Collection<Long> idsArticles,
                                           @Param("entree") TypeMvtStk entree);

    /**
     * Les articles qui ont encore du stock dans un site — positif ou negatif. Un site ne se ferme
     * pas tant qu'il en reste : sa marchandise disparaitrait des comptes sans etre sortie.
     */
    @Query("select m.articles.id from MvtStk m where m.site.id = :idSite group by m.articles.id " +
            "having coalesce(sum(case when m.typMvt = :entree then m.quantite else -m.quantite end), 0) <> 0")
    java.util.List<Long> articlesEnStockDansSite(@Param("idSite") Long idSite, @Param("entree") TypeMvtStk entree);

    /**
     * Le stock d'un article dans un site, lot par lot : identifiant du lot (nul pour le stock sans
     * lot), quantite. C'est la matiere de l'allocation « premier perime, premier sorti ».
     */
    @Query("select l.id, coalesce(sum(case when m.typMvt = :entree then m.quantite else -m.quantite end), 0) " +
            "from MvtStk m left join m.lot l " +
            "where m.articles.id = :idArticle and m.site.id = :idSite group by l.id")
    java.util.List<Object[]> stocksParLotDansSite(@Param("idArticle") Long idArticle, @Param("idSite") Long idSite,
                                                  @Param("entree") TypeMvtStk entree);

    /** Le stock de chaque lot, site par site : lot, site, quantite. Pour les alertes et le rappel. */
    @Query("select m.lot.id, m.site.id, coalesce(sum(case when m.typMvt = :entree then m.quantite " +
            "else -m.quantite end), 0) from MvtStk m " +
            "where m.lot.id in :idsLots and m.site is not null group by m.lot.id, m.site.id")
    java.util.List<Object[]> stocksDesLots(@Param("idsLots") java.util.Collection<Long> idsLots,
                                           @Param("entree") TypeMvtStk entree);

    /**
     * Ce qu'une vente a fait sortir de chaque lot d'un article, net de ce qu'elle a deja rendu :
     * lot (nul possible), quantite. C'est ce qu'une annulation ou une correction remet en place.
     */
    @Query("select l.id, coalesce(sum(case when m.typMvt = :sortie then m.quantite else -m.quantite end), 0) " +
            "from MvtStk m left join m.lot l " +
            "where m.idVente = :idVente and m.articles.id = :idArticle group by l.id")
    java.util.List<Object[]> netParLotDeLaVente(@Param("idVente") Long idVente, @Param("idArticle") Long idArticle,
                                                @Param("sortie") TypeMvtStk sortie);

    /**
     * Ce qu'un transfert a fait partir de chaque lot d'un article et qui n'est pas encore arrive :
     * lot (nul possible), quantite. Deux lignes du meme article se recoivent ainsi l'une apres
     * l'autre sans recevoir deux fois le meme lot.
     */
    @Query("select l.id, coalesce(sum(case when m.typMvt = :sortie then m.quantite else -m.quantite end), 0) " +
            "from MvtStk m left join m.lot l " +
            "where m.idTransfert = :idTransfert and m.articles.id = :idArticle group by l.id")
    java.util.List<Object[]> netParLotDuTransfert(@Param("idTransfert") Long idTransfert,
                                                    @Param("idArticle") Long idArticle,
                                                    @Param("sortie") TypeMvtStk sortie);

    /** Les ventes d'un lot : vente, quantite nette sortie. Le cœur du rappel. */
    @Query("select m.idVente, coalesce(sum(case when m.typMvt = :sortie then m.quantite else -m.quantite end), 0) " +
            "from MvtStk m where m.lot.id = :idLot and m.idVente is not null group by m.idVente")
    java.util.List<Object[]> ventesDuLot(@Param("idLot") Long idLot, @Param("sortie") TypeMvtStk sortie);
}
