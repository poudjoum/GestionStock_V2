package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Bon d'achat obtenu en echange de points de fidelite.
 *
 * Emis avec un code unique lisible sous forme de QR / code barre sur l'application mobile,
 * presentable au caissier lors d'un passage en caisse.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "bon_achat")
public class BonDAchat extends AbstractEntity {

    @Column(name = "code_bon", nullable = false, unique = true, length = 32)
    private String codeBon;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_client_fidelite", nullable = false)
    private CompteClientFidelite client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_entreprise", nullable = false)
    private Entreprise entreprise;

    @Column(name = "points_utilises", nullable = false)
    private int pointsUtilises;

    @Column(name = "montant_fcfa", nullable = false)
    private BigDecimal montantFcfa;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutBonDAchat statut = StatutBonDAchat.ACTIF;

    @Column(name = "date_emission", nullable = false)
    private Instant dateEmission;

    @Column(name = "date_expiration", nullable = false)
    private Instant dateExpiration;

    @Column(name = "date_utilisation")
    private Instant dateUtilisation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_vente_utilisation")
    private Vente venteUtilisation;
}
