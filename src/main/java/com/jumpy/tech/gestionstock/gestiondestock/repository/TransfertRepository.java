package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Transfert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TransfertRepository extends JpaRepository<Transfert, Long> {

    List<Transfert> findAllByIdEntrepriseOrderByIdDesc(Long idEntreprise);

    @Query(value = "select nextval('transfert_reference_seq')", nativeQuery = true)
    Long prochaineReference();
}
