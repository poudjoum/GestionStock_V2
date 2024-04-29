package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Fournisseur;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class FournisseurDto {
    private Long id;
    private String nom;

    private String prenom;

    private AdresseDto adresse;

    private String tel;

    private String photo;

    private String mail;

    @JsonIgnore
    private List<CommandeFourDto> commandeFournisseur;

    public static FournisseurDto fromEntity(Fournisseur fournisseur) {
        if(fournisseur==null) {
            return null;
        }
        return FournisseurDto.builder()
                .id(fournisseur.getId())
                .nom(fournisseur.getNom())
                .prenom(fournisseur.getPrenom())
                .photo(fournisseur.getPhoto())
                .tel(fournisseur.getTel())
                .adresse(AdresseDto.fromEntity(fournisseur.getAdresse()))
                .mail(fournisseur.getMail())
                .build();
    }

    public static Fournisseur toEntity(FournisseurDto dto) {

        if(dto==null) {
            return null;
        }
        Fournisseur four=new Fournisseur();
        four.setId(dto.getId());
        four.setNom(dto.getNom());
        four.setPrenom(dto.getPrenom());
        four.setPhoto(dto.getPhoto());
        four.setTel(dto.getTel());
        four.setAdresse(AdresseDto.toEntity(dto.getAdresse()));
        four.setMail(dto.getMail());
        return four;
    }
}
