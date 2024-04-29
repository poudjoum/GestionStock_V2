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
@Table(name="Fournisseur")
public class Fournisseur extends AbstractEntity{
    @Column(name="nom")
    private String nom;
    @Column(name="prenom")
    private String prenom;
    @Embedded
    private Adresse adresse;
    @Column(name="NumTel")
    private String tel;
    @Column(name="Photo")
    private String photo;
    @Column(name="idEntreprise")
    private Long idEntreprise;
    @Column(name="Mail")
    private String Mail;
    @OneToMany(mappedBy="fournisseur")
    private List<CommandeFour> commandeFournisseur;

}
