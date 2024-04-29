package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneCmndeFournisseur;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LigneCmndeFourRepository extends JpaRepository<LigneCmndeFournisseur,Long> {
}
