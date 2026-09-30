package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.AbonnementPush;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AbonnementPushRepository extends JpaRepository<AbonnementPush, Long> {

    Optional<AbonnementPush> findByAdresse(String adresse);

    List<AbonnementPush> findAllByUtilisateurId(Long idUtilisateur);
}
