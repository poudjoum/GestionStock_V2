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
 * Ce qui part, dans l'unite ou on le charge — des cartons —, et ce qui arrive. `contenance`
 * convertit l'un et l'autre en unites de base pour le stock.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = "transfert")
@Entity
@Table(name = "ligne_transfert")
public class LigneTransfert extends AbstractEntity {

    @ManyToOne
    @JoinColumn(name = "id_transfert", nullable = false)
    private Transfert transfert;

    @ManyToOne
    @JoinColumn(name = "id_article", nullable = false)
    private Article article;

    @ManyToOne
    @JoinColumn(name = "id_conditionnement")
    private Conditionnement conditionnement;

    @Column(name = "contenance", nullable = false)
    private BigDecimal contenance = BigDecimal.ONE;

    @Column(name = "quantite", nullable = false)
    private BigDecimal quantite;

    @Column(name = "quantite_recue")
    private BigDecimal quantiteRecue;

    @Column(name = "motif_ecart")
    private String motifEcart;

    @Column(name = "id_entreprise")
    private Long idEntreprise;
}
