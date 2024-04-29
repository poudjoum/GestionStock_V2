package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.CommandeFour;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Builder
@Data

public class CommandeFourDto {

    private Long id;
    private String code;

    private Instant dateCommande;

    private FournisseurDto fournisseur;

    private List<LigneCmndeFournisseurDto> ligneCmndeFournisseur;

    public static CommandeFourDto fromEntity(CommandeFour cmdeF) {
        if(cmdeF==null) {
            return null;
        }
        return CommandeFourDto.builder()
                .id(cmdeF.getId())
                .code(cmdeF.getCode())
                .dateCommande(cmdeF.getDateCommande())
                .fournisseur(FournisseurDto.fromEntity(cmdeF.getFournisseur()))
                .build();
    }
    public static CommandeFour toEntity(CommandeFourDto dto) {
        if(dto==null) {
            return null;
        }
        CommandeFour cf=new CommandeFour();
        cf.setId(dto.getId());
        cf.setCode(dto.getCode());
        cf.setDateCommande(dto.getDateCommande());
        cf.setFournisseur(FournisseurDto.toEntity(dto.getFournisseur()));
        return cf;
    }
}
