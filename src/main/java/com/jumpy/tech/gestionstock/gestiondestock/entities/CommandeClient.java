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
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name="CommandeClient")
public class CommandeClient extends AbstractEntity{

    @Column(name="Code")
    private String code;
    @Column(name="DateCommande")
    private Instant dateCommande;
    @Column(name="idEntreprise")
    private Long idEntreprise;
    @Enumerated(EnumType.STRING)
    @Column(name="etat", nullable = false, length = 20)
    private EtatCommande etat;
    /** Pourquoi le reste de la commande ne sera pas servi. */
    @Column(name="motif_cloture")
    private String motifCloture;
    @ManyToOne
    @JoinColumn(name="idClient")
    private Client client;
    @OneToMany(mappedBy="commandeClient")
    private List<LigneCmndeClient> ligneCmndeClient;
}
