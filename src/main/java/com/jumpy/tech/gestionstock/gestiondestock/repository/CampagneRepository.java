package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Campagne;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CampagneRepository extends JpaRepository<Campagne, Long> {

    List<Campagne> findAllByIdEntrepriseOrderByDateDebutDesc(Long idEntreprise);

    /** Toutes les campagnes en cours, tous magasins confondus : la vitrine de l'application. */
    @Query("""
            select c from Campagne c
            where c.arretee = false and c.dateDebut <= :jour and c.dateFin >= :jour
            order by c.dateFin asc
            """)
    List<Campagne> toutesEnCours(@Param("jour") LocalDate jour);

    /** Les campagnes en cours d'un magasin. */
    @Query("""
            select c from Campagne c
            where c.idEntreprise = :idEntreprise and c.arretee = false
              and c.dateDebut <= :jour and c.dateFin >= :jour
            """)
    List<Campagne> enCours(@Param("idEntreprise") Long idEntreprise, @Param("jour") LocalDate jour);

    /**
     * Une campagne pendant laquelle l'achat a eu lieu, et qui n'est pas encore finie : c'est elle
     * qui rend le ticket scannable.
     */
    @Query("""
            select count(c) > 0 from Campagne c
            where c.idEntreprise = :idEntreprise and c.arretee = false
              and c.dateDebut <= :jourAchat and c.dateFin >= :jourAchat
              and c.dateFin >= :aujourdhui
            """)
    boolean ticketScannable(@Param("idEntreprise") Long idEntreprise,
                            @Param("jourAchat") LocalDate jourAchat,
                            @Param("aujourdhui") LocalDate aujourdhui);

    /** Si un achat fait ce jour-la tombe dans une campagne, finie ou non. */
    @Query("""
            select count(c) > 0 from Campagne c
            where c.idEntreprise = :idEntreprise and c.arretee = false
              and c.dateDebut <= :jourAchat and c.dateFin >= :jourAchat
            """)
    boolean achatEnCampagne(@Param("idEntreprise") Long idEntreprise, @Param("jourAchat") LocalDate jourAchat);
}
