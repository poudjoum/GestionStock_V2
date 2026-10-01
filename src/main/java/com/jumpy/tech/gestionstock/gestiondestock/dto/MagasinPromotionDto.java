package com.jumpy.tech.gestionstock.gestiondestock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MagasinPromotionDto {
    private Long id;
    private String nom;
    private String description;
    private String ville;
    private String adresse;
    private String telephone;
    private String logo;
    private BigDecimal montantParPoint;
    /** Ce que vaut un point une fois echange, et le moins qu'on puisse echanger. */
    private BigDecimal valeurPointFcfa;
    private int pointsMinimumBon;
    private int dureeValiditeBonJours;
    private boolean fideliteActive;
    private int pointsClient;
}
