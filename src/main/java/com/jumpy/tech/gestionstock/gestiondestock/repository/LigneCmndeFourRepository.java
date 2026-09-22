package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneCmndeFournisseur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LigneCmndeFourRepository extends JpaRepository<LigneCmndeFournisseur,Long> {

    /** Les lignes d'une commande, relues a la livraison pour faire entrer la marchandise. */
    List<LigneCmndeFournisseur> findAllByCommandeFournisseurId(Long idCommandeFournisseur);

    /**
     * De quoi calculer un cout d'achat moyen : montant total achete et quantite totale, par
     * article, sur ce qui est **reellement entre en magasin**.
     *
     * C'est `quantiteLivree` qui compte, et non `quantite` : l'un dit ce qui est arrive, l'autre
     * ce qui a ete commande. La requete ne regardait auparavant que les commandes LIVREE, et une
     * commande partiellement livree — ou clôturee sur son reliquat — faisait entrer sa marchandise
     * en stock sans lui donner de valeur. Le magasin comptait alors des sacs de ciment valorises a
     * zero, et l'ecart ne se voyait nulle part.
     *
     * Une ligne dont rien n'est arrive s'exclut d'elle-meme : sa quantite livree vaut zero, et
     * elle ne pese ni au numerateur ni au denominateur. Les commandes en preparation, validees ou
     * annulees sortent donc du calcul sans qu'on ait a nommer leur etat.
     *
     * Le quotient est fait en Java plutot qu'en SQL, pour n'avoir pas a se demander ce que la base
     * repond quand la quantite vaut zero.
     */
    @org.springframework.data.jpa.repository.Query(
            "select l.articles.id, " +
            "       coalesce(sum(l.quantiteLivree * l.prixUnitaire), 0), " +
            "       coalesce(sum(l.quantiteLivree), 0) " +
            "from LigneCmndeFournisseur l " +
            "where l.quantiteLivree > 0 and l.articles.id in :idsArticles " +
            "group by l.articles.id")
    List<Object[]> coutsAchetes(
            @org.springframework.data.repository.query.Param("idsArticles") java.util.Collection<Long> idsArticles);
}
