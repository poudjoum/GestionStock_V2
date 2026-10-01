package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.StatutBonDAchat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BonDAchatDto {
    private Long id;
    private String codeBon;
    private Long idEntreprise;
    private String nomMagasin;
    private int pointsUtilises;
    private BigDecimal montantFcfa;
    private StatutBonDAchat statut;
    private Instant dateEmission;
    private Instant dateExpiration;
    private Instant dateUtilisation;
    private boolean utilisable;
}
