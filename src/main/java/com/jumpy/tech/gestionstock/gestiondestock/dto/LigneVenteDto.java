package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneVente;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Builder
@Data
public class LigneVenteDto {
    private Long id;
    @JsonIgnore
    private VenteDto vente;
    private BigDecimal quantite;
    private BigDecimal prixUnitaire;
    private ArticleDto article;

    public static LigneVenteDto fromEntity(LigneVente lgv) {
        if(lgv==null) {
            return null;
        }
        return LigneVenteDto.builder()
                .id(lgv.getId())
                .vente(VenteDto.fromEntity(lgv.getVente()))
                .quantite(lgv.getQuantite())
                .prixUnitaire(lgv.getPrixUnitaire())
                .article(ArticleDto.fromEntity(lgv.getArticles()))
                .build();
    }
    public static LigneVente toEntity(LigneVenteDto dto) {
        if(dto==null) {
            return null;
        }
        LigneVente lv=new LigneVente();
        lv.setId(dto.getId());
        lv.setVente(VenteDto.toEntity(dto.getVente()));
        lv.setQuantite(dto.getQuantite());
        lv.setPrixUnitaire(dto.getPrixUnitaire());
        lv.setArticles(ArticleDto.toEntity(dto.getArticle()));
        return lv;
    }
}
