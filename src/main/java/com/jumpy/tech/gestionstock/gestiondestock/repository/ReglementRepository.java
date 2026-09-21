package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Reglement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ReglementRepository extends JpaRepository<Reglement, Long> {

    List<Reglement> findAllByFactureIdOrderByDateReglementAsc(Long idFacture);

    /** Ce qui a ete encaisse sur une facture. `coalesce` : une facture sans reglement vaut zero. */
    @Query("select coalesce(sum(r.montant), 0) from Reglement r where r.facture.id = :idFacture")
    BigDecimal totalReglePour(@Param("idFacture") Long idFacture);

    /**
     * Les totaux de plusieurs factures en une requete.
     *
     * Une liste de factures affiche le reste a payer de chacune : les interroger une par une
     * ferait une requete par ligne, ce que personne ne remarque sur dix factures et que tout le
     * monde subit sur mille.
     */
    @Query("select r.facture.id, coalesce(sum(r.montant), 0) from Reglement r " +
            "where r.facture.id in :idsFactures group by r.facture.id")
    List<Object[]> totauxRegles(@Param("idsFactures") Collection<Long> idsFactures);

    boolean existsByFactureId(Long idFacture);

    /**
     * Ce qui est entre sur une periode, regroupe par moyen de paiement.
     *
     * Deux requetes plutot qu'un `:idEntreprise is null` dans la clause : un parametre nul dont
     * PostgreSQL ne peut pas deduire le type fait echouer la requete, et la lisibilite y gagne.
     */
    @Query("select r.mode, coalesce(sum(r.montant), 0), count(r) from Reglement r " +
            "where r.dateReglement >= :debut and r.dateReglement < :fin " +
            "and r.idEntreprise = :idEntreprise group by r.mode order by r.mode")
    List<Object[]> totauxParModePourEntreprise(@Param("debut") Instant debut,
                                               @Param("fin") Instant fin,
                                               @Param("idEntreprise") Long idEntreprise);

    @Query("select r.mode, coalesce(sum(r.montant), 0), count(r) from Reglement r " +
            "where r.dateReglement >= :debut and r.dateReglement < :fin group by r.mode order by r.mode")
    List<Object[]> totauxParMode(@Param("debut") Instant debut, @Param("fin") Instant fin);

    /**
     * Le detail d'une periode. Borne haute exclue, comme pour les totaux ci-dessus : le derive
     * `Between` de Spring Data inclut les deux bornes, et les deux lectures d'une meme journee ne
     * se recouperaient plus.
     */
    @Query("select r from Reglement r where r.dateReglement >= :debut and r.dateReglement < :fin " +
            "and r.idEntreprise = :idEntreprise order by r.dateReglement desc")
    Page<Reglement> detailPourEntreprise(@Param("debut") Instant debut, @Param("fin") Instant fin,
                                         @Param("idEntreprise") Long idEntreprise, Pageable pageable);

    @Query("select r from Reglement r where r.dateReglement >= :debut and r.dateReglement < :fin " +
            "order by r.dateReglement desc")
    Page<Reglement> detail(@Param("debut") Instant debut, @Param("fin") Instant fin, Pageable pageable);
}
