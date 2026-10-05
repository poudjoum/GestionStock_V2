package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Lot;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Un lot, ce qu'il en reste, et ou en est sa date. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LotDto {
    private Long id;
    private Long idArticle;
    private String codeArticle;
    private String designation;
    private String numero;
    private LocalDate datePeremption;
    private TypeDate typeDate;
    /** Dans le site lu ; en vue « tous sites », la somme. */
    private BigDecimal quantite;
    private Long idSite;
    private String nomSite;
    /** PERIME, BIENTOT ou BON. */
    private String etat;
    /** Jours avant la date — negatif une fois passee ; nul sans date. */
    private Long joursRestants;
    private List<StockSiteDto> parSite;

    public static LotDto de(Lot lot) {
        return LotDto.builder()
                .id(lot.getId())
                .idArticle(lot.getArticle().getId())
                .codeArticle(lot.getArticle().getCodeArticle())
                .designation(lot.getArticle().getDesignation())
                .numero(lot.getNumero())
                .datePeremption(lot.getDatePeremption())
                .typeDate(lot.getArticle().getTypeDate())
                .build();
    }
}
