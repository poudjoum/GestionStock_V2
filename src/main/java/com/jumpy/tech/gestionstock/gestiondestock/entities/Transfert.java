package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Un envoi de marchandise d'un site de l'entreprise a un autre. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "transfert")
public class Transfert extends AbstractEntity {

    @Column(name = "id_entreprise", nullable = false)
    private Long idEntreprise;

    @Column(name = "reference", nullable = false, length = 30)
    private String reference;

    @ManyToOne
    @JoinColumn(name = "id_site_source", nullable = false)
    private Site source;

    @ManyToOne
    @JoinColumn(name = "id_site_destination", nullable = false)
    private Site destination;

    @Enumerated(EnumType.STRING)
    @Column(name = "etat", nullable = false, length = 12)
    private EtatTransfert etat;

    @Column(name = "commentaire")
    private String commentaire;

    @Column(name = "date_expedition")
    private Instant dateExpedition;

    @Column(name = "date_reception")
    private Instant dateReception;
}
