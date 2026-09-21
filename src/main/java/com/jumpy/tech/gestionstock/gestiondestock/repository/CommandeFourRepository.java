package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.CommandeFour;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommandeFourRepository extends JpaRepository<CommandeFour,Long> {

    Optional<CommandeFour> findCommandeFourByCode(String code);

    java.util.List<CommandeFour> findAllByIdEntreprise(Long idEntreprise);

    Optional<CommandeFour> findCommandeFourByCodeAndIdEntreprise(String code, Long idEntreprise);

    /**
     * Les commandes, filtrables par etat et par texte libre.
     *
     * Le filtre par etat est ce dont le quai a besoin : le magasinier qui decharge un camion
     * cherche les commandes qu'il peut recevoir — `VALIDEE` et `PARTIELLEMENT_LIVREE` — et non
     * l'historique complet des achats de la maison. `/all` les rendait toutes, sans pagination.
     *
     * `q` porte sur le code de la commande et le nom du fournisseur : c'est ce qui figure sur le
     * bon de livraison qu'on a en main.
     */
    @org.springframework.data.jpa.repository.Query("""
            select c from CommandeFour c
            where (:filtrer = false
                   or c.idEntreprise = :idEntreprise
                   or (:idEntreprise is null and c.idEntreprise is null))
              and (:etats is null or c.etat in :etats)
              and (:q = ''
                   or lower(c.code) like lower(concat('%', :q, '%'))
                   or lower(c.fournisseur.nom) like lower(concat('%', :q, '%')))
            """)
    org.springframework.data.domain.Page<CommandeFour> rechercher(
            @org.springframework.data.repository.query.Param("filtrer") boolean filtrer,
            @org.springframework.data.repository.query.Param("idEntreprise") Long idEntreprise,
            @org.springframework.data.repository.query.Param("etats")
            java.util.List<com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande> etats,
            @org.springframework.data.repository.query.Param("q") String q,
            org.springframework.data.domain.Pageable pageable);
}
