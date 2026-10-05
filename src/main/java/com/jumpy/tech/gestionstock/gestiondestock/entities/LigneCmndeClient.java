package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "LigneCmndeClient")

public class LigneCmndeClient extends AbstractEntity{

    @ManyToOne
    @JoinColumn(name="idArticle")
    private Article articles;
    @ManyToOne
    @JoinColumn(name="idCommandeClient")
    private CommandeClient commandeClient;
    @Column(name="Quantite")
    private BigDecimal quantite;
    /** Ce qui a deja ete servi sur cette ligne ; le reste du client en est la difference. */
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
