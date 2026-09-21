package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Fournisseur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FournisseurRepository extends JpaRepository<Fournisseur,Long> {
    Optional<Fournisseur> findFournisseurByNom(String nomFournisseur);

    java.util.List<Fournisseur> findAllByIdEntreprise(Long idEntreprise);

    org.springframework.data.domain.Page<Fournisseur> findAllByIdEntreprise(
            Long idEntreprise, org.springframework.data.domain.Pageable pageable);

    Optional<Fournisseur> findFournisseurByNomAndIdEntreprise(String nomFournisseur, Long idEntreprise);

    /**
     * Retrouver un fournisseur : un bout de nom, de courriel ou de numero.
     *
     * `Mail` porte une majuscule dans l'entite ; le chemin JPQL suit le champ, pas la propriete.
     */
    @org.springframework.data.jpa.repository.Query("""
            select f from Fournisseur f
            where (:filtrer = false
                   or f.idEntreprise = :idEntreprise
                   or (:idEntreprise is null and f.idEntreprise is null))
              and (:q = ''
                   or lower(f.nom) like lower(concat('%', :q, '%'))
                   or lower(f.prenom) like lower(concat('%', :q, '%'))
                   or lower(f.Mail) like lower(concat('%', :q, '%'))
                   or f.tel like concat('%', :q, '%'))
            """)
    org.springframework.data.domain.Page<Fournisseur> rechercher(
            @org.springframework.data.repository.query.Param("filtrer") boolean filtrer,
            @org.springframework.data.repository.query.Param("idEntreprise") Long idEntreprise,
            @org.springframework.data.repository.query.Param("q") String q,
            org.springframework.data.domain.Pageable pageable);
}
