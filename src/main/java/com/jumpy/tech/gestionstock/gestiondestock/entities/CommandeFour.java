package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper=true)
@Entity
@Table(name="CommandeFournisseur")

public class CommandeFour extends AbstractEntity{
    @Column(name="Code")
    private String code;
    @Column(name="dateCommande")
    private Instant dateCommande;
    @Column(name="idEntreprise")
    private Long idEntreprise;
    @ManyToOne
    @JoinColumn(name="idFournisseur")
    private Fournisseur fournisseur;
    @OneToMany(mappedBy="commandeFournisseur")
    private List<LigneCmndeFournisseur> ligneCmndeFournisseur;
}
