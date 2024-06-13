package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Utilisateur;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Builder
@Data

public class UserDto {
    private Long id;
    private String nom;

    private String prenoms;
    private String motdepasse;

    private Instant dateNaissance;

    private String photo;

    private AdresseDto adresse;
    private String numTel;

    private EntrepriseDto entreprise;

    private List<RoleDto> roles;

    public static UserDto fromEntity(Utilisateur ut) {
        if(ut==null) {
            return null;
            // TODO Auto-generated method stub
        }

        return UserDto.builder()
                .id(ut.getId())
                .nom(ut.getNom())
                .prenoms(ut.getPrenoms())
                .motdepasse(ut.getMotdepasse())
                .photo(ut.getPhoto())
                .dateNaissance(ut.getDateNaissance())
                .entreprise(EntrepriseDto.fromEntity(ut.getEntreprise()))
                .adresse(AdresseDto.fromEntity(ut.getAdresse()))
                .numTel(ut.getNumTel())
                .roles(ut.getRoles()!=null?
                        ut.getRoles().stream()
                                .map(

        RoleDto::fromEntity)
                                .collect(Collectors.toList()):null)
                .build();
    }

    public static Utilisateur toEntity(UserDto dto) {
        if(dto==null) {
            return null;
            // TODO Auto-generated method stub
        }
        Utilisateur us=new Utilisateur();
        us.setId(dto.getId());
        us.setNom(dto.getNom());
        us.setPrenoms(dto.getPrenoms());
        us.setMotdepasse(dto.getMotdepasse());
        us.setPhoto(dto.getPhoto());
        us.setDateNaissance(dto.getDateNaissance());
        us.setAdresse(AdresseDto.toEntity(dto.getAdresse()));
        us.setNumTel(dto.getNumTel());
        us.setEntreprise(EntrepriseDto.toEntity(dto.getEntreprise()));
        us.setRoles(dto.getRoles()!=null?
                dto.getRoles().stream()
                        .map(RoleDto::toEntity)
                        .collect(Collectors.toList()):null);

        return us;
    }
}
