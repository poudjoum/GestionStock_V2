package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * La facture d'une vente.
 *
 * Document fige : les montants sont stockes et non recalcules a l'affichage. Une facture qui
 * relirait les prix courants changerait de valeur dans le dos de celui qui la detient.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "facture")
public class Facture extends AbstractEntity {

    @Column(name = "numero", nullable = false, length = 30)
    private String numero;

    @Column(name = "date_emission", nullable = false)
    private Instant dateEmission;

    @OneToOne
    @JoinColumn(name = "id_vente", nullable = false)
    private Vente vente;

    @Column(name = "total_ht", nullable = false)
    private BigDecimal totalHt;

    @Column(name = "total_tva", nullable = false)
    private BigDecimal totalTva;

    @Column(name = "total_ttc", nullable = false)
    private BigDecimal totalTtc;

    /** Une facture ne se supprime pas : elle s'annule et reste lisible. */
    @Column(name = "annulee", nullable = false)
    private boolean annulee;

    @Column(name = "id_entreprise")
    private Long idEntreprise;

    @OneToMany(mappedBy = "facture")
    private List<LigneFacture> lignes;
}
