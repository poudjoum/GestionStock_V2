package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.ModeReglement;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Reglement;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
@Data
public class ReglementDto {

    private Long id;
    private Long idFacture;
    private String numeroFacture;
    private Instant dateReglement;
    private BigDecimal montant;
    private ModeReglement mode;
    private String reference;

    public static ReglementDto fromEntity(Reglement reglement) {
        if (reglement == null) {
            return null;
        }
        return ReglementDto.builder()
                .id(reglement.getId())
                .idFacture(reglement.getFacture() == null ? null : reglement.getFacture().getId())
                .numeroFacture(reglement.getFacture() == null ? null : reglement.getFacture().getNumero())
                .dateReglement(reglement.getDateReglement())
                .montant(reglement.getMontant())
                .mode(reglement.getMode())
                .reference(reglement.getReference())
                .build();
    }
}
