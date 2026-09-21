package com.jumpy.tech.gestionstock.gestiondestock.repository;


import com.jumpy.tech.gestionstock.gestiondestock.entities.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UtilisateurRepository extends JpaRepository<Utilisateur,Long> {
    Optional<Utilisateur> findUtilisateurByEmail(String email);
    Optional<Utilisateur> findUtilisateurByUsername(String username);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);

    /**
     * Les comptes d'une entreprise. `null` rend ceux qui n'y sont pas rattaches.
     *
     * Les roles sont charges avec : ils sont en LAZY, et le DTO les lit. Sans ce graphe, c'est une
     * requete par compte — ou, hors transaction, une LazyInitializationException.
     */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "roles")
    java.util.List<Utilisateur> findAllByEntrepriseId(Long idEntreprise);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "roles")
    java.util.List<Utilisateur> findAllBy();
}
