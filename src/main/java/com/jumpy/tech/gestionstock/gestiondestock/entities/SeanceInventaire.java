package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Une seance d'inventaire : le comptage du magasin, d'un bout a l'autre.
 *
 * Elle fige a son ouverture ce que le logiciel croit avoir, recoit ensuite ce que l'on trouve sur
 * les etageres, et ne touche au stock qu'a la validation — par des mouvements portant le motif
 * INVENTAIRE, jamais par une reecriture directe. Le stock demeure ainsi ce qu'il a toujours ete :
 * la somme de son histoire, et non une valeur que quelqu'un aurait posee.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "seance_inventaire")
public class SeanceInventaire extends AbstractEntity {

    @Column(name = "reference", nullable = false, length = 30)
    private String reference;

    @Column(name = "date_ouverture", nullable = false)
    private Instant dateOuverture;

    /** Nulle tant que la seance est ouverte. */
    @Column(name = "date_cloture")
    private Instant dateCloture;

    // En clair : un statut mappe en rang se reinterpreterait si l'on intercalait une valeur, et
    // tout l'historique changerait de sens en silence.
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutSeanceInventaire statut;

    /** Ce que l'on note en ouvrant ou en fermant : « inventaire de fin de mois », « rayon peinture ». */
    @Column(name = "commentaire", length = 255)
    private String commentaire;

    @Column(name = "id_entreprise")
    private Long idEntreprise;
}
