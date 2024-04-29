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
    @Column(name="prixUnitaire")
    private BigDecimal prixUnitaire;
    @Column(name="idEntreprise")
    private Long idEntreprise;
}
