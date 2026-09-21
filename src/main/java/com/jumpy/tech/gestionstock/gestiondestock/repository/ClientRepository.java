package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClientRepository extends JpaRepository<Client, Long> {

    List<Client> findAllByIdEntreprise(Long idEntreprise);

    Page<Client> findAllByIdEntreprise(Long idEntreprise, Pageable pageable);

    /**
     * Retrouver un client au comptoir : un bout de nom, de courriel ou de numero.
     *
     * Le telephone d'abord, parce que c'est ce qu'on demande a quelqu'un qu'on ne retrouve pas
     * dans la liste. `q` absent rend la liste entiere.
     */
    @Query("""
            select c from Client c
            where (:filtrer = false
                   or c.idEntreprise = :idEntreprise
                   or (:idEntreprise is null and c.idEntreprise is null))
              and (:q = ''
                   or lower(c.nom) like lower(concat('%', :q, '%'))
                   or lower(c.prenoms) like lower(concat('%', :q, '%'))
                   or lower(c.mail) like lower(concat('%', :q, '%'))
                   or c.numTel like concat('%', :q, '%'))
            """)
    Page<Client> rechercher(@Param("filtrer") boolean filtrer,
                            @Param("idEntreprise") Long idEntreprise,
                            @Param("q") String q,
                            Pageable pageable);
}
