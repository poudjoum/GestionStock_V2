package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Builder
@Data
public class ArticleDto {
    private Long Id;
    private String codeArticle;
    private String designation;
    private BigDecimal prixUnitaireHt;
    private BigDecimal tauxTva;
    private BigDecimal prixUnitaireTTC;
    private String Photo;
    private CategoryDto category;

    public static ArticleDto fromEntity(Article art) {
        if (art == null) {
            return null;

        }
        return ArticleDto.builder()
                .codeArticle(art.getCodeArticle())
                .Id(art.getId())
                .designation(art.getDesignation())
                .Photo(art.getPhoto())
                .prixUnitaireHt(art.getPrixUnitaire())
                .tauxTva(art.getTauxTva())
                .prixUnitaireTTC(art.getPrixUnitTTC())
                .category(CategoryDto.fromEntity(art.getCategory()))
                .build();


    }

    public static Article toEntity(ArticleDto dto) {
        if (dto == null) {
            return null;
        }
        Article art = new Article();
        art.setId(dto.getId());
        art.setCodeArticle(dto.getCodeArticle());
        art.setCategory(CategoryDto.toEntity(dto.getCategory()));
        art.setDesignation(dto.getDesignation());
        art.setPhoto(dto.getPhoto());
        art.setPrixUnitaire(dto.getPrixUnitaireHt());
        art.setTauxTva(dto.getTauxTva());
        art.setPrixUnitTTC(dto.getPrixUnitaireTTC());
        return art;
    }
}
