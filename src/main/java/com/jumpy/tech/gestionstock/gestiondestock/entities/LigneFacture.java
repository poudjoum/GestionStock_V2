package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Une ligne de facture.
 *
 * Elle ne pointe pas vers l'article : elle en recopie le code, la designation, le prix et le taux
 * de TVA au moment de l'emission. Un article renomme, repricé ou supprime ne doit pas changer une
 * facture deja remise au client.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "ligne_facture")
public class LigneFacture extends AbstractEntity {

    @ManyToOne
    @JoinColumn(name = "id_facture", nullable = false)
    private Facture facture;

    @Column(name = "code_article")
    private String codeArticle;

    @Column(name = "designation")
    private String designation;

    @Column(name = "quantite", nullable = false)
    private BigDecimal quantite;

    @Column(name = "prix_unitaire_ht", nullable = false)
    private BigDecimal prixUnitaireHt;

    @Column(name = "taux_tva", nullable = false)
    private BigDecimal tauxTva;

    @Column(name = "montant_ht", nullable = false)
    private BigDecimal montantHt;

    @Column(name = "montant_tva", nullable = false)
    private BigDecimal montantTva;

    @Column(name = "montant_ttc", nullable = false)
    private BigDecimal montantTtc;
}
