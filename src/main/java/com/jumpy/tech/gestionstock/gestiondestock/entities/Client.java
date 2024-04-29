package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name="client")

public class Client extends AbstractEntity{
    @Column(name="noms")
    private String nom;
    @Column(name="prenoms")
    private String prenoms;
    @Column(name="photo")
    private String photo;
    @Column(name="email")
    private String mail;
    @Embedded
    private Adresse adresse;
    @Column(name="telephone")
    private String numTel;
    @Column(name="idEntreprise")
    private Long idEntreprise;
    @OneToMany(mappedBy="client")
    private List<CommandeClient> commandeClients;

}
