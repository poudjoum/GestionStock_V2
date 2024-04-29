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
    @Column(name="prixUnitaire")
    private BigDecimal prixUnitaire;
    @Column(name="idEntreprise")
    private Long idEntreprise;

}
