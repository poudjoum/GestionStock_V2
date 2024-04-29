package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Entreprise;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class EntrepriseDto {
    private Long id;
    private String nom;

    private String description;

    private AdresseDto adresse;

    private String registreCommerce;

    private String email;

    private String photo;

    private String tel;

    private String siteWeb;
    @JsonIgnore
    private List<UserDto> user;

    public static EntrepriseDto fromEntity(Entreprise en) {
        if(en==null) {
            return null;
        }
        return EntrepriseDto.builder()
                .id(en.getId())
                .nom(en.getNom())
                .description(en.getDescription())
                .photo(en.getPhoto())
                .registreCommerce(en.getRegistreCommerce())
                .email(en.getEmail())
                .siteWeb(en.getSiteWeb())
                .tel(en.getTel())
                .adresse(AdresseDto.fromEntity(en.getAdresse()))
                .build();

    }
    public static Entreprise toEntity(EntrepriseDto dto) {
        if(dto==null) {
            return null;
        }
        Entreprise en=new Entreprise();
        en.setId(dto.getId());
        en.setNom(dto.getNom());
        en.setDescription(dto.getDescription());
        en.setPhoto(dto.getPhoto());
        en.setRegistreCommerce(dto.getRegistreCommerce());
        en.setEmail(dto.getEmail());
        en.setSiteWeb(dto.getSiteWeb());
        en.setTel(dto.getTel());
        en.setAdresse(AdresseDto.toEntity(dto.getAdresse()));

        return en;
    }
}
