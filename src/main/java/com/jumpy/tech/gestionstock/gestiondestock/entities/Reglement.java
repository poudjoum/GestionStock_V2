package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un encaissement sur une facture.
 *
 * Plusieurs peuvent porter sur la meme : un acompte a la commande, le solde a la livraison est le
 * cas ordinaire. Ce qui a ete paye se somme a la lecture ; rien n'est tenu a jour sur la facture.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "reglement")
public class Reglement extends AbstractEntity {

    @ManyToOne
    @JoinColumn(name = "id_facture", nullable = false)
    private Facture facture;

    @Column(name = "date_reglement", nullable = false)
    private Instant dateReglement;

    @Column(name = "montant", nullable = false)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 20)
    private ModeReglement mode;

    /** Numero de transaction, de cheque ou de virement : par ou l'encaissement se retrouve. */
    @Column(name = "reference")
    private String reference;

    @Column(name = "id_entreprise")
    private Long idEntreprise;
}
