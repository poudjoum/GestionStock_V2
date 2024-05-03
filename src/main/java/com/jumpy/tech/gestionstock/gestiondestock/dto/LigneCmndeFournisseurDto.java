package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneCmndeFournisseur;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class LigneCmndeFournisseurDto {
    private Long id;
    private ArticleDto article;
    @JsonIgnore
    private CommandeFourDto cmndeFour;
    private BigDecimal quantite;
    private BigDecimal prixUnitaire;

    public static LigneCmndeFournisseurDto fromEntity(LigneCmndeFournisseur ligneCmndeFour){
        if(ligneCmndeFour==null){
            return null;
        }
        return LigneCmndeFournisseurDto.builder()
                .id(ligneCmndeFour.getId())
                .article(ArticleDto.fromEntity(ligneCmndeFour.getArticles()))
                .cmndeFour(CommandeFourDto.fromEntity(ligneCmndeFour.getCommandeFournisseur()))
                .quantite(ligneCmndeFour.getQuantite())
                .prixUnitaire(ligneCmndeFour.getPrixUnitaire())
                .build();
    }
    public static LigneCmndeFournisseur toEntity(LigneCmndeFournisseurDto dto){
        if(dto==null){
            return null;
        }
        LigneCmndeFournisseur lcc=new LigneCmndeFournisseur();
        lcc.setId(dto.getId());
        lcc.setArticles(ArticleDto.toEntity(dto.getArticle()));
        lcc.setPrixUnitaire(dto.getPrixUnitaire());
        lcc.setQuantite(dto.getQuantite());
        return lcc;

    }
}

