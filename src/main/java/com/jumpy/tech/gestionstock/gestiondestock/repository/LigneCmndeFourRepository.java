package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneCmndeFournisseur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LigneCmndeFourRepository extends JpaRepository<LigneCmndeFournisseur,Long> {

    /**
     * Ce qui est deja commande et pas encore arrive dans un site, par article, en unites de base.
     * Les brouillons comptent : une proposition de reapprovisionnement transformee en brouillon ne
     * doit pas etre proposee une seconde fois le lendemain.
     */
    @org.springframework.data.jpa.repository.Query(
            "select l.articles.id, coalesce(sum((l.quantite - l.quantiteLivree) * l.contenance), 0) " +
            "from LigneCmndeFournisseur l " +
            "where l.commandeFournisseur.site.id = :idSite and l.commandeFournisseur.etat in :etats " +
            "and l.quantite > l.quantiteLivree group by l.articles.id")
    List<Object[]> enCommandeDansSite(
            @org.springframework.data.repository.query.Param("idSite") Long idSite,
            @org.springframework.data.repository.query.Param("etats")
            java.util.Collection<com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande> etats);

    /**
     * Les achats passes des articles, le plus recent en tete : le premier de chaque article dit son
     * fournisseur habituel, l'unite dans laquelle on l'achete et ce qu'il a coute.
     */
    @org.springframework.data.jpa.repository.Query(
            "select l from LigneCmndeFournisseur l join fetch l.commandeFournisseur c join fetch c.fournisseur " +
            "where l.articles.id in :idsArticles and c.etat <> com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande.ANNULEE " +
            "order by c.dateCommande desc, l.id desc")
    List<LigneCmndeFournisseur> achatsRecents(
            @org.springframework.data.repository.query.Param("idsArticles") java.util.Collection<Long> idsArticles);

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
     *
     * Le denominateur est en unites de base : trois cartons de 24 achetes 9 000 l'un font
     * 27 000 pour 72 bouteilles, et le cout moyen est celui d'une bouteille — l'unite du stock
     * qu'il sert a valoriser.
     */
    @org.springframework.data.jpa.repository.Query(
            "select l.articles.id, " +
            "       coalesce(sum(l.quantiteLivree * l.prixUnitaire), 0), " +
            "       coalesce(sum(l.quantiteLivree * l.contenance), 0) " +
            "from LigneCmndeFournisseur l " +
            "where l.quantiteLivree > 0 and l.articles.id in :idsArticles " +
            "group by l.articles.id")
    List<Object[]> coutsAchetes(
            @org.springframework.data.repository.query.Param("idsArticles") java.util.Collection<Long> idsArticles);
}
