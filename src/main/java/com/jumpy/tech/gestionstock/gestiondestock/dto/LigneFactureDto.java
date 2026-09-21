package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneFacture;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Builder
@Data
public class LigneFactureDto {

    private Long id;
    private String codeArticle;
    private String designation;
    private BigDecimal quantite;
    private BigDecimal prixUnitaireHt;
    private BigDecimal tauxTva;
    private BigDecimal montantHt;
    private BigDecimal montantTva;
    private BigDecimal montantTtc;

    public static LigneFactureDto fromEntity(LigneFacture ligne) {
        if (ligne == null) {
            return null;
        }
        return LigneFactureDto.builder()
                .id(ligne.getId())
                .codeArticle(ligne.getCodeArticle())
                .designation(ligne.getDesignation())
                .quantite(ligne.getQuantite())
                .prixUnitaireHt(ligne.getPrixUnitaireHt())
                .tauxTva(ligne.getTauxTva())
                .montantHt(ligne.getMontantHt())
                .montantTva(ligne.getMontantTva())
                .montantTtc(ligne.getMontantTtc())
                .build();
    }
}
