package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Entreprise;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Builder
@Data
public class EntrepriseDto {

    /** Le taux en vigueur au Cameroun, retenu quand l'enregistrement n'en precise pas d'autre. */
    public static final BigDecimal TAUX_TVA_PAR_DEFAUT = new BigDecimal("19.25");

    private Long id;
    private String nom;

    private String description;

    private AdresseDto adresse;

    private String registreCommerce;

    private String email;

    private String photo;

    private String tel;

    private String siteWeb;

    /** Si l'entreprise collecte la TVA. Vrai par defaut. */
    private Boolean assujettieTva;

    /** Taux applique par defaut, en pourcentage. 19,25 au Cameroun. */
    private BigDecimal tauxTva;

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
                .registreCommerce(en.getRegistreCommerce())
                .email(en.getEmail_Entreprise())
                .siteWeb(en.getSiteWeb())
                .tel(en.getTel())
                .adresse(AdresseDto.fromEntity(en.getAdresse()))
                .assujettieTva(en.isAssujettieTva())
                .tauxTva(en.getTauxTva())
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

        en.setRegistreCommerce(dto.getRegistreCommerce());
        en.setEmail_Entreprise(dto.getEmail());
        en.setSiteWeb(dto.getSiteWeb());
        en.setTel(dto.getTel());
        en.setAdresse(AdresseDto.toEntity(dto.getAdresse()));
        // Une entreprise est assujettie sauf mention contraire : c'est le cas courant, et une
        // omission ne doit pas la faire passer pour exoneree.
        en.setAssujettieTva(dto.getAssujettieTva() == null || dto.getAssujettieTva());
        en.setTauxTva(dto.getTauxTva() == null ? TAUX_TVA_PAR_DEFAUT : dto.getTauxTva());

        return en;
    }
}
