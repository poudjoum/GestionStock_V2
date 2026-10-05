package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * Une facon de vendre ou d'acheter un article : le carton de 24, le sac de 50 kg, la boite de 30.
 *
 * Il ne porte pas de stock. Il dit combien d'unites de base il contient, et c'est cette
 * contenance qui convertit une ligne de vente ou de commande en mouvement de stock.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = "article")
@Entity
@Table(name = "conditionnement")
public class Conditionnement extends AbstractEntity {

    @ManyToOne
    @JoinColumn(name = "id_article", nullable = false)
    private Article article;

    @Column(name = "id_entreprise")
    private Long idEntreprise;

    @Column(name = "libelle", nullable = false, length = 60)
    private String libelle;

    /** Combien d'unites de base de l'article il contient. */
    @Column(name = "quantite_unites", nullable = false)
    private BigDecimal quantiteUnites;

    /** Hors taxes. Le taux de TVA est celui de l'article. */
    @Column(name = "prix_vente_ht")
    private BigDecimal prixVenteHt;

    @Column(name = "vendable", nullable = false)
    private boolean vendable = true;

    @Column(name = "achetable", nullable = false)
    private boolean achetable = true;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;
}
