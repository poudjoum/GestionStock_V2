package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
 * Tracabilite d'un ticket scanne et reclame par un client.
 *
 * Le code_ticket porte une contrainte unique : le meme papier ne peut jamais etre scanne deux fois.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "ticket_reclame")
public class TicketReclame extends AbstractEntity {

    @Column(name = "code_ticket", nullable = false, unique = true, length = 12)
    private String codeTicket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_client_fidelite", nullable = false)
    private CompteClientFidelite client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_entreprise", nullable = false)
    private Entreprise entreprise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_vente", nullable = false)
    private Vente vente;

    @Column(name = "points_attribues", nullable = false)
    private int pointsAttribues;

    @Column(name = "montant_achat_ttc", nullable = false)
    private BigDecimal montantAchatTtc;

    @Column(name = "date_reclamation", nullable = false)
    private Instant dateReclamation;
}
