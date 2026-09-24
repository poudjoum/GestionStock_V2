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
    /**
     * Un compte ferme reste en base : l'employe parti demeure l'auteur des ventes qu'il a
     * saisies, et les effacer avec lui rendrait cet historique illisible.
     */
    // @Builder.Default, sans quoi le builder rend un compte `actif = false` : ferme des sa
    // creation, et incapable de se connecter.
    @Builder.Default
    @Column(name="actif", nullable = false)
    private boolean actif = true;
    /**
     * Le mot de passe est provisoire et doit etre change avant toute autre chose.
     *
     * Pose a l'inscription d'un commerce : l'editeur choisit un mot de passe, l'envoie au gerant
     * par courriel, et ne doit pas le connaitre plus longtemps. C'est ce drapeau qui rend
     * acceptable de l'avoir transmis en clair.
     */
    @Builder.Default
    @Column(name = "motdepasse_a_changer", nullable = false)
    private boolean motdepasseAChanger = false;
    @Embedded
    private Adresse adresse;
    private String numTel;
    @ManyToOne
    @JoinColumn(name="idEntreprise")
    private Entreprise entreprise;
   @ManyToMany(fetch = FetchType.LAZY)
   @JoinTable(name="user_roles",joinColumns = @JoinColumn(name="user_id"),inverseJoinColumns = @JoinColumn(name="role_id"))
   // Sans @Builder.Default, Lombok ignore l'initialisation : un Utilisateur.builder() rendait un
   // objet dont `roles` valait null, et UserDetailsImpl.build lui applique un `.stream()` — une
   // NullPointerException a la connexion.
   @Builder.Default
    private Set<Role> roles=new HashSet<>();

    // Appele par /api/auth/signup. Son corps etait vide : l'inscription enregistrait un
    // utilisateur sans identifiant ni mot de passe, que la connexion ne retrouvait jamais.
    public Utilisateur(String username, String email, String motdepasse) {
        this.username = username;
        this.email = email;
        this.motdepasse = motdepasse;
        this.actif = true;
        // `roles` est initialise ici, et ce n'est pas une redondance avec la declaration du champ.
        //
        // `@Builder.Default` retire l'initialisation du champ pour la deplacer dans le builder :
        // elle ne s'execute donc plus dans aucun constructeur, et `getRoles()` rend `null`.
        // L'inscription ne s'en apercevait pas — elle appelle `setRoles` juste apres — mais tout
        // autre appelant recoit une NullPointerException au premier `.add()`.
        this.roles = new HashSet<>();
    }
}
