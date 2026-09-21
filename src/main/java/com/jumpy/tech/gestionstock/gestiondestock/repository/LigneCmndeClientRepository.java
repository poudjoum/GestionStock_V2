package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneCmndeClient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LigneCmndeClientRepository extends JpaRepository<LigneCmndeClient,Long> {

    List<LigneCmndeClient> findAllByCommandeClientId(Long idCommandeClient);
}
