package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Reglement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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
}
