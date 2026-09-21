package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper=true)
@Entity
// `user` est un mot reserve de PostgreSQL : une table de ce nom ne se cree et ne s'interroge
// qu'entre guillemets, et la moindre requete ecrite a la main casse.
@Table(name="utilisateur")
public class Utilisateur extends AbstractEntity{
    @Column(name="nom")
    private String nom;
    @Column(name="prenoms")
    private String prenoms;
    @Column(name="email")
    private String email;
    @Column(name="dateNaissance")
    private Instant dateNaissance;
    @Column(name="photo")
    private String photo;
    @Column(name="Motdepasse")
    private String motdepasse;
    @Column(name="username")
    private String username;
    @Embedded
    private Adresse adresse;
    private String numTel;
    @ManyToOne
    @JoinColumn(name="idEntreprise")
    private Entreprise entreprise;
   @ManyToMany(fetch = FetchType.LAZY)
   @JoinTable(name="user_roles",joinColumns = @JoinColumn(name="user_id"),inverseJoinColumns = @JoinColumn(name="role_id"))
    private Set<Role> roles=new HashSet<>();

    // Appele par /api/auth/signup. Son corps etait vide : l'inscription enregistrait un
    // utilisateur sans identifiant ni mot de passe, que la connexion ne retrouvait jamais.
    public Utilisateur(String username, String email, String motdepasse) {
        this.username = username;
        this.email = email;
        this.motdepasse = motdepasse;
    }
}
