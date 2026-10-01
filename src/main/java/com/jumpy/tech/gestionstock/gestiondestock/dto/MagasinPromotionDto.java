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
    private boolean fideliteActive;
    private int pointsClient;
}
