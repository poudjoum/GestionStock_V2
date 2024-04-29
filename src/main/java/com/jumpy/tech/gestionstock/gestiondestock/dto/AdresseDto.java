package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Adresse;
import lombok.Builder;
import lombok.Data;

@Builder
@Data

public class AdresseDto {
    private String adresse1;

    private String adresse2;

    private String ville;

    private String codePostale;

    private String pays ;

    public static AdresseDto fromEntity(Adresse adresse){
        if(adresse==null){
            return null;
        }
        return AdresseDto.builder()
                .adresse1(adresse.getAdresse1())
                        .adresse2(adresse.getAdresse2())
                                .codePostale(adresse.getCodePostal())
                                        .pays(adresse.getPays())
                                                .ville(adresse.getVille()).
                build();

    }
    public static Adresse toEntity(AdresseDto dto){
        if(dto==null){
            return null;
        }
        Adresse adresse=new Adresse();
        adresse.setAdresse1(dto.getAdresse1());
        adresse.setAdresse2(dto.getAdresse2());
        adresse.setCodePostal(dto.getCodePostale());
        adresse.setPays(dto.getPays());
        adresse.setVille(dto.getVille());

        return adresse;
    }
}
