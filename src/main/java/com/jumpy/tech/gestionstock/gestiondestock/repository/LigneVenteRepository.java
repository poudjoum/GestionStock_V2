package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneVente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LigneVenteRepository extends JpaRepository<LigneVente,Long> {

    /** Les lignes d'une vente, relues pour remettre la marchandise en magasin a l'annulation. */
    List<LigneVente> findAllByVenteId(Long idVente);

    /**
     * Ce que chaque article a rapporte sur une periode : article, chiffre d'affaires HT, quantite
     * vendue en unites de base, date de la derniere vente. Les ventes annulees n'ont rien rapporte.
     * `idSite` nul : tous les sites de l'entreprise.
     */
    @org.springframework.data.jpa.repository.Query(
            "select l.articles.id, coalesce(sum(l.quantite * l.prixUnitaire), 0), " +
            "coalesce(sum(l.quantite * l.contenance), 0), max(v.datevente) " +
            "from LigneVente l join l.vente v " +
            "where v.idEntreprise = :idEntreprise and v.annulee = false and v.datevente >= :depuis " +
            "and (:idSite is null or v.site.id = :idSite) " +
            "group by l.articles.id")
    List<Object[]> ventesParArticle(@org.springframework.data.repository.query.Param("idEntreprise") Long idEntreprise,
                                    @org.springframework.data.repository.query.Param("idSite") Long idSite,
                                    @org.springframework.data.repository.query.Param("depuis") java.time.Instant depuis);
}
