package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Facture;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Builder
@Data
public class FactureDto {

    private Long id;
    private String numero;
    private Instant dateEmission;
    private Long idVente;
    private String codeVente;
    private BigDecimal totalHt;
    private BigDecimal totalTva;
    private BigDecimal totalTtc;
    private boolean annulee;
    private Long idEntreprise;
    private List<LigneFactureDto> lignes;

    public static FactureDto fromEntity(Facture facture) {
        return fromEntity(facture, null);
    }

    /**
     * Les lignes sont passees a part plutot que lues depuis l'entite : une facture se consulte
     * souvent en liste, et charger les lignes de chacune pour les jeter aussitot coute cher.
     */
    public static FactureDto fromEntity(Facture facture, List<LigneFactureDto> lignes) {
        if (facture == null) {
            return null;
        }
        return FactureDto.builder()
                .id(facture.getId())
                .numero(facture.getNumero())
                .dateEmission(facture.getDateEmission())
                .idVente(facture.getVente() == null ? null : facture.getVente().getId())
                .codeVente(facture.getVente() == null ? null : facture.getVente().getCode())
                .totalHt(facture.getTotalHt())
                .totalTva(facture.getTotalTva())
                .totalTtc(facture.getTotalTtc())
                .annulee(facture.isAnnulee())
                .idEntreprise(facture.getIdEntreprise())
                .lignes(lignes)
                .build();
    }

    public static FactureDto avecLignes(Facture facture, List<com.jumpy.tech.gestionstock.gestiondestock.entities.LigneFacture> lignes) {
        return fromEntity(facture, lignes.stream()
                .map(LigneFactureDto::fromEntity)
                .collect(Collectors.toList()));
    }
}
