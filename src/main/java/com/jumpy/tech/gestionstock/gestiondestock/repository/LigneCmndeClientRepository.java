package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;
import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneCmndeClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface LigneCmndeClientRepository extends JpaRepository<LigneCmndeClient,Long> {

    List<LigneCmndeClient> findAllByCommandeClientId(Long idCommandeClient);

    /**
     * Ce que les commandes en cours retiennent d'un article dans le site qui les livre, en unites
     * de base : ce qui reste a servir, ligne par ligne.
     *
     * Calcule et non tenu a jour, comme le stock : un compteur de reservation se desynchronise a la
     * premiere commande annulee par un chemin qui l'aurait oublie. La commande `exclue` est celle
     * qu'on est en train de servir — elle puise dans sa propre reservation.
     */
    @Query("select coalesce(sum((l.quantite - l.quantiteLivree) * l.contenance), 0) from LigneCmndeClient l " +
            "where l.articles.id = :idArticle and l.commandeClient.etat in :etats " +
            "and coalesce(l.commandeClient.siteExpedition.id, l.commandeClient.site.id) = :idSite " +
            "and l.quantite > l.quantiteLivree " +
            "and (:exclue is null or l.commandeClient.id <> :exclue)")
    BigDecimal reserveDansSite(@Param("idArticle") Long idArticle, @Param("idSite") Long idSite,
                               @Param("etats") Collection<EtatCommande> etats, @Param("exclue") Long exclue);

    /** La meme reservation pour plusieurs articles, site par site : article, site, quantite. */
    @Query("select l.articles.id, coalesce(l.commandeClient.siteExpedition.id, l.commandeClient.site.id), " +
            "coalesce(sum((l.quantite - l.quantiteLivree) * l.contenance), 0) from LigneCmndeClient l " +
            "where l.articles.id in :idsArticles and l.commandeClient.etat in :etats " +
            "and l.quantite > l.quantiteLivree " +
            "group by l.articles.id, coalesce(l.commandeClient.siteExpedition.id, l.commandeClient.site.id)")
    List<Object[]> reservesParSite(@Param("idsArticles") Collection<Long> idsArticles,
                                   @Param("etats") Collection<EtatCommande> etats);
}
