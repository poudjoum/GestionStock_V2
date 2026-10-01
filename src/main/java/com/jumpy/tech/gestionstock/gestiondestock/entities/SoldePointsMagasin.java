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

/**
 * Solde des points de fidelite d'un client dans un magasin donne.
 *
 * Les points gagnes dans un magasin s'y consomment et ne se melangent pas a ceux des autres.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "solde_points_magasin")
public class SoldePointsMagasin extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_client_fidelite", nullable = false)
    private CompteClientFidelite client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_entreprise", nullable = false)
    private Entreprise entreprise;

    @Column(name = "solde_points", nullable = false)
    private int soldePoints;

    @Column(name = "points_cumules_total", nullable = false)
    private int pointsCumulesTotal;
}
