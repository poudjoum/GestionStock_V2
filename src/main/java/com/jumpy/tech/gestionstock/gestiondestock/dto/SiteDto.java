package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Site;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeSite;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteDto {
    private Long id;
    private String nom;
    private TypeSite type;
    private String adresse;
    private String telephone;
    /** En lecture : le site principal se designe par la migration ou a la creation de l'entreprise. */
    private Boolean principal;
    private Boolean actif;

    public static SiteDto fromEntity(Site site) {
        if (site == null) {
            return null;
        }
        return SiteDto.builder()
                .id(site.getId())
                .nom(site.getNom())
                .type(site.getType())
                .adresse(site.getAdresse())
                .telephone(site.getTelephone())
                .principal(site.isPrincipal())
                .actif(site.isActif())
                .build();
    }
}
