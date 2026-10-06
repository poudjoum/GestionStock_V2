package com.jumpy.tech.gestionstock.gestiondestock.rapport;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface ResumeEnvoyeRepository extends JpaRepository<ResumeEnvoye, Long> {

    boolean existsByIdEntrepriseAndTypeAndDebut(Long idEntreprise, ResumeEnvoye.Type type, LocalDate debut);
}
