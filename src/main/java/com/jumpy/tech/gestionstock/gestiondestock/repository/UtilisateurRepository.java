package com.jumpy.tech.gestionstock.gestiondestock.repository;


import com.jumpy.tech.gestionstock.gestiondestock.entities.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UtilisateurRepository extends JpaRepository<Utilisateur,Long> {
    Optional<Utilisateur> findUtilisateurByEmail(String email);
    Optional<Utilisateur> findUtilisateurByUsername(String username);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);
}
