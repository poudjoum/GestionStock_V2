package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.CommandeClient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommandeClientRepository extends JpaRepository<CommandeClient,Long> {
    Optional<CommandeClient> findCommandeClientByCode(String code);

    java.util.List<CommandeClient> findAllByIdEntreprise(Long idEntreprise);

    Optional<CommandeClient> findCommandeClientByCodeAndIdEntreprise(String code, Long idEntreprise);
}
