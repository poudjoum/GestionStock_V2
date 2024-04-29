package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper=true)
@Entity
@Table(name="Entreprise")

public class Entreprise extends AbstractEntity{
    @Column(name="Nom")
    private String nom;
    @Column(name="Description")
    private String description;
    @Embedded
    private Adresse adresse;
    @Column(name="RegistreCommerce")
    private String registreCommerce;
    @Column(name="Email")
    private String email;
    @Column(name="photo")
    private String photo;
    @Column(name="telephone")
    private String tel;
    @Column(name="Siteweb")
    private String siteWeb;
    @OneToMany(mappedBy="entreprise")
    private List<User> users;
}
