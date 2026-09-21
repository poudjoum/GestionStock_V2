package com.jumpy.tech.gestionstock.gestiondestock.dto;


import com.jumpy.tech.gestionstock.gestiondestock.entities.CommandeClient;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Builder
@Data

public class CommandeClientDto {
    private Long Id;
    private String code;

    private Instant dateCmnde;

    private ClientDto client;
    private Long idEntreprise;
    private EtatCommande etat;


    private List<LigneCommandeClientDto> ligneCmndeClients;

    public static CommandeClientDto fromEntity(CommandeClient cmdClient) {
        if(cmdClient==null) {
            return null;
        }
        return CommandeClientDto.builder()
                .Id(cmdClient.getId())
                .code(cmdClient.getCode())
                .dateCmnde(cmdClient.getDateCommande())
                .client(ClientDto.fromEntity(cmdClient.getClient()))
                .idEntreprise(cmdClient.getIdEntreprise())
                .etat(cmdClient.getEtat())
                .build();
    }
    public static  CommandeClient toEntity(CommandeClientDto dto) {
        if(dto==null) {
            return null;
        }
        CommandeClient cl= new CommandeClient();
        cl.setId(dto.getId());
        cl.setCode(dto.getCode());
        cl.setDateCommande(dto.getDateCmnde());
        cl.setClient(ClientDto.toEntity(dto.getClient()));
        cl.setIdEntreprise(dto.getIdEntreprise());
        cl.setEtat(dto.getEtat());

        return cl;
    }
}
