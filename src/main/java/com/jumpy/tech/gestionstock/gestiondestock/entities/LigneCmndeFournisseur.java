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

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper=true)
@Entity

public class LigneCmndeFournisseur extends AbstractEntity{
    @ManyToOne
    @JoinColumn(name="idArticle")
    private Article articles;
    @ManyToOne
    @JoinColumn(name="idCommandeFournisseur")
    private CommandeFour commandeFournisseur;
    @Column(name="Quantite")
    private BigDecimal quantite;
    /**
     * Ce qui est deja arrive sur cette ligne ; le reste attendu en est la difference. La quantite
     * livree se porte sur la ligne et non sur la commande : un compteur global ne dirait pas quel
     * article manque.
     */
    @Column(name="quantite_livree", nullable = false)
    private BigDecimal quantiteLivree = BigDecimal.ZERO;
    @Column(name="prixUnitaire")
    private BigDecimal prixUnitaire;
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
