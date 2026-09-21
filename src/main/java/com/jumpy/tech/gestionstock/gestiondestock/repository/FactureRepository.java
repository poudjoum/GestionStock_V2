package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Facture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FactureRepository extends JpaRepository<Facture, Long> {

    Optional<Facture> findByNumero(String numero);

    Optional<Facture> findByVenteId(Long idVente);

    org.springframework.data.domain.Page<Facture> findAllByIdEntreprise(
            Long idEntreprise, org.springframework.data.domain.Pageable pageable);

    Optional<Facture> findByNumeroAndIdEntreprise(String numero, Long idEntreprise);

    boolean existsByVenteId(Long idVente);

    /**
     * Les factures, filtrables par numero, par client et par ce qu'il reste a encaisser.
     *
     * « Qui me doit de l'argent » est la question du comptable, et la liste paginee n'y repondait
     * pas : il fallait feuilleter toutes les factures en lisant les statuts un par un.
     *
     * Le statut de reglement n'est pas une colonne — il se deduit de la somme des reglements,
     * comme le stock se deduit de ses mouvements. Le filtre porte donc sur cette somme, calculee
     * ici en sous-requete plutot que rapatriee page par page.
     *
     * Une facture annulee ne doit plus rien : elle sort des impayees, sans quoi le comptable
     * courrait apres un argent que personne ne doit.
     */
    @Query("""
            select f from Facture f
            where (:filtrer = false
                   or f.idEntreprise = :idEntreprise
                   or (:idEntreprise is null and f.idEntreprise is null))
              and (:q = ''
                   or lower(f.numero) like lower(concat('%', :q, '%'))
                   or lower(f.nomClient) like lower(concat('%', :q, '%')))
              and (:statut = ''
                   or (:statut = 'IMPAYEE' and f.annulee = false
                       and (select coalesce(sum(r.montant), 0) from Reglement r where r.facture = f) = 0)
                   or (:statut = 'PARTIELLEMENT_REGLEE' and f.annulee = false
                       and (select coalesce(sum(r.montant), 0) from Reglement r where r.facture = f) > 0
                       and (select coalesce(sum(r.montant), 0) from Reglement r where r.facture = f) < f.totalTtc)
                   or (:statut = 'REGLEE' and f.annulee = false
                       and (select coalesce(sum(r.montant), 0) from Reglement r where r.facture = f) >= f.totalTtc)
                   or (:statut = 'DUES' and f.annulee = false
                       and (select coalesce(sum(r.montant), 0) from Reglement r where r.facture = f) < f.totalTtc)
                   or (:statut = 'ANNULEE' and f.annulee = true))
            """)
    org.springframework.data.domain.Page<Facture> rechercher(
            @Param("filtrer") boolean filtrer,
            @Param("idEntreprise") Long idEntreprise,
            @Param("q") String q,
            @Param("statut") String statut,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Le prochain numero, pris dans la sequence de la base.
     *
     * Un compteur calcule en Java — « le plus grand numero plus un » — donnerait le meme numero a
     * deux factures emises en meme temps. La sequence, elle, ne rend jamais deux fois la meme
     * valeur, meme sous plusieurs instances de l'application.
     */
    @Query(value = "select nextval('facture_numero_seq')", nativeQuery = true)
    Long prochainNumero();
}
