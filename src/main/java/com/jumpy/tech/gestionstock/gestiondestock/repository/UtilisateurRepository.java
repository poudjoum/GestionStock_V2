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

    /**
     * Les comptes ouverts d'une entreprise qui portent l'un de ces roles.
     *
     * `distinct` parce qu'un compte portant deux des roles demandes serait sinon rendu deux fois,
     * et recevrait deux fois la meme alerte.
     *
     * Les comptes fermes sont exclus : notifier quelqu'un qui ne peut plus se connecter ne sert
     * qu'a gonfler une table que personne ne lira.
     */
    @org.springframework.data.jpa.repository.Query("""
            select distinct u from Utilisateur u join u.roles r
            where u.actif = true
              and r.roleName in :roles
              and (u.entreprise.id = :idEntreprise
                   or (:idEntreprise is null and u.entreprise is null))
            """)
    java.util.List<Utilisateur> findDestinatairesActifs(
            @org.springframework.data.repository.query.Param("idEntreprise") Long idEntreprise,
            @org.springframework.data.repository.query.Param("roles")
            java.util.List<com.jumpy.tech.gestionstock.gestiondestock.entities.ERole> roles);

    /**
     * Le nombre de comptes par entreprise, en une requete.
     *
     * Le tableau de bord de l'editeur montre une ligne par commerce : les compter un par un
     * ferait une requete par ligne affichee, et la page grandirait avec sa clientele.
     */
    @org.springframework.data.jpa.repository.Query(
            "select u.entreprise.id, count(u) from Utilisateur u "
            + "where u.entreprise is not null group by u.entreprise.id")
    java.util.List<Object[]> comptesParEntreprise();
}
