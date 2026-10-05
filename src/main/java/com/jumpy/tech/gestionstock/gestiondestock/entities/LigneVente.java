package com.jumpy.tech.gestionstock.gestiondestock.entities;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper=true)
@Entity
public class LigneVente extends AbstractEntity{
    @ManyToOne
    @JoinColumn(name="idVente")
    private Vente vente;
    private BigDecimal quantite;
    private BigDecimal prixUnitaire;
    @ManyToOne
    @JoinColumn(name="idArticle")
    private Article articles;
    @Column(name="idEntreprise")
    private Long idEntreprise;

    /**
     * Le conditionnement dans lequel la ligne a ete saisie ; nul, elle est a l'unite de base.
     * `quantite` et `prixUnitaire` sont alors exprimes dans ce conditionnement.
     */
    @ManyToOne
    @JoinColumn(name="id_conditionnement")
    private Conditionnement conditionnement;

    /**
     * Combien d'unites de base vaut une unite de la ligne, figee a la saisie. Le stock bouge de
     * `quantite x contenance` : un carton redefini plus tard ne reecrit pas ce qui est deja sorti.
     */
    @Column(name="contenance", nullable = false)
    private BigDecimal contenance = BigDecimal.ONE;
}
