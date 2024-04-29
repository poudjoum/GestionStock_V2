package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Client;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data

public class CommandeClientDto {
    private Long Id;
    private String nom;

    private String prenoms;

    private String photo;

    private String mail;

    private AdresseDto adresse;

    private String numTel;
    @JsonIgnore
    private List<CommandeClientDto> commandeClients;

    public static ClientDto fromEntity(Client client) {
        if(client==null) {
            return null;
        }
        return ClientDto.builder()
                .Id(client.getId())
                .nom(client.getNom())
                .prenoms(client.getPrenoms())
                .adresse(AdresseDto.fromEntity(client.getAdresse()))
                .mail(client.getMail())
                .numTel(client.getNumTel())
                .build();
    }
    public static Client toEntity(ClientDto clientDto) {
        if(clientDto==null) {
            return null;
        }
        Client client= new Client();
        client.setId(clientDto.getId());
        client.setNom(clientDto.getNom());
        client.setPrenoms(clientDto.getPrenoms());
        client.setAdresse(AdresseDto.toEntity(clientDto.getAdresse()));
        client.setNumTel(clientDto.getNumTel());
        client.setMail(clientDto.getMail());
        client.setPhoto(clientDto.getPhoto());
        return client;
    }
}
