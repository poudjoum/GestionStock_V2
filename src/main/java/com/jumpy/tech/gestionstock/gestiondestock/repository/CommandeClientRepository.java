package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.CommandeClient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommandeClientRepository extends JpaRepository<CommandeClient,Long> {
    Optional<CommandeClient> findCommandeClientByCode(String code);

    java.util.List<CommandeClient> findAllByIdEntreprise(Long idEntreprise);

    Optional<CommandeClient> findCommandeClientByCodeAndIdEntreprise(String code, Long idEntreprise);

    /**
     * Les commandes, filtrables par etat et par texte libre, limitees aux sites de l'appelant : la
     * commande prise au magasin, ou celle que son site livre. `q` porte sur le code et le nom du
     * client — ce qu'on a sous les yeux quand il appelle pour savoir ou en est sa commande.
     */
    @org.springframework.data.jpa.repository.Query("""
            select c from CommandeClient c
              left join c.site s
              left join c.siteExpedition se
              left join c.client cl
            where (:filtrer = false
                   or c.idEntreprise = :idEntreprise
                   or (:idEntreprise is null and c.idEntreprise is null))
              and (:etats is null or c.etat in :etats)
              and (:tousSites = true or s.id in :sites or se.id in :sites)
              and (:q = ''
                   or lower(c.code) like lower(concat('%', :q, '%'))
                   or lower(cl.nom) like lower(concat('%', :q, '%'))
                   or lower(cl.prenoms) like lower(concat('%', :q, '%')))
            """)
    org.springframework.data.domain.Page<CommandeClient> rechercher(
            @org.springframework.data.repository.query.Param("filtrer") boolean filtrer,
            @org.springframework.data.repository.query.Param("idEntreprise") Long idEntreprise,
            @org.springframework.data.repository.query.Param("etats")
            java.util.List<com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande> etats,
            @org.springframework.data.repository.query.Param("tousSites") boolean tousSites,
            @org.springframework.data.repository.query.Param("sites") java.util.Collection<Long> sites,
            @org.springframework.data.repository.query.Param("q") String q,
            org.springframework.data.domain.Pageable pageable);
}
