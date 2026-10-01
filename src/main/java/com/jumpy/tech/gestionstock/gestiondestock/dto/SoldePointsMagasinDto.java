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
    private boolean fideliteActive;
}
