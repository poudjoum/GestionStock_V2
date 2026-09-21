package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.CommandeFour;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;
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
    private Long idEntreprise;
    private EtatCommande etat;
    /** Renseigne quand la commande a ete cloturee sans avoir tout recu. */
    private String motifCloture;

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
                .idEntreprise(cmdeF.getIdEntreprise())
                .etat(cmdeF.getEtat())
                .motifCloture(cmdeF.getMotifCloture())
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
        cf.setIdEntreprise(dto.getIdEntreprise());
        cf.setEtat(dto.getEtat());
        cf.setMotifCloture(dto.getMotifCloture());
        return cf;
    }
}
