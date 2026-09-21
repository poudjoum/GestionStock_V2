package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneFacture;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LigneFactureRepository extends JpaRepository<LigneFacture, Long> {

    List<LigneFacture> findAllByFactureId(Long idFacture);
}
