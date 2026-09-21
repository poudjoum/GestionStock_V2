package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneCmndeFournisseur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LigneCmndeFourRepository extends JpaRepository<LigneCmndeFournisseur,Long> {

    /** Les lignes d'une commande, relues a la livraison pour faire entrer la marchandise. */
    List<LigneCmndeFournisseur> findAllByCommandeFournisseurId(Long idCommandeFournisseur);

    /**
     * De quoi calculer un cout d'achat moyen : montant total achete et quantite totale, par
     * article, sur les seules commandes **livrees**.
     *
     * Les commandes en preparation ne comptent pas — la marchandise n'est pas arrivee, et son
     * prix n'a donc encore rien coute. Le quotient est fait en Java plutot qu'en SQL, pour n'avoir
     * pas a se demander ce que la base repond quand la quantite vaut zero.
     */
    @org.springframework.data.jpa.repository.Query(
            "select l.articles.id, coalesce(sum(l.quantite * l.prixUnitaire), 0), coalesce(sum(l.quantite), 0) " +
            "from LigneCmndeFournisseur l " +
            "where l.commandeFournisseur.etat = :livree and l.articles.id in :idsArticles " +
            "group by l.articles.id")
    List<Object[]> coutsAchetes(
            @org.springframework.data.repository.query.Param("idsArticles") java.util.Collection<Long> idsArticles,
            @org.springframework.data.repository.query.Param("livree")
            com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande livree);
}
