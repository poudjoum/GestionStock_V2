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
public class SoldePointsMagasinDto {
    private Long idEntreprise;
    private String nomMagasin;
    private String ville;
    private String logo;
    private int soldePoints;
    private int pointsCumulesTotal;
    private BigDecimal montantParPoint;
    /** Ce que vaut un point une fois echange, et le moins qu'on puisse echanger. */
    private BigDecimal valeurPointFcfa;
    private int pointsMinimumBon;
    private boolean fideliteActive;
}
