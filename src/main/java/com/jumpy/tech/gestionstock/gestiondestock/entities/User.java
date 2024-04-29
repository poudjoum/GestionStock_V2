package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper=true)
@Entity
@Table(name="user")
public class User extends AbstractEntity{
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
    @Embedded
    private Adresse adresse;
    private String numTel;
    @ManyToOne
    @JoinColumn(name="idEntreprise")
    private Entreprise entreprise;
    @OneToMany(mappedBy="user")
    private List<Role> roles;
}
