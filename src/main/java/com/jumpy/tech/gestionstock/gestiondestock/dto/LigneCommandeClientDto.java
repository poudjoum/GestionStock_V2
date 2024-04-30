package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneCmndeClient;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
@Data
@Builder
public class LigneCommandeClientDto {
    private Long id;
    private ArticleDto article;
    @JsonIgnore
    private CommandeClientDto cmndeClient;
    private BigDecimal quantite;
    private BigDecimal prixUnitaire;

    public static LigneCommandeClientDto fromEntity(LigneCmndeClient ligneCmndeClient){
        if(ligneCmndeClient==null){
            return null;
        }
        return LigneCommandeClientDto.builder()
                        .id(ligneCmndeClient.getId())
                                .article(ArticleDto.fromEntity(ligneCmndeClient.getArticles()))
                                        .cmndeClient(CommandeClientDto.fromEntity(ligneCmndeClient.getCommandeClient()))
                                                .quantite(ligneCmndeClient.getQuantite())
                                                        .prixUnitaire(ligneCmndeClient.getPrixUnitaire())
                                                                .build();
    }
    public static LigneCmndeClient toEntity(LigneCommandeClientDto dto){
        if(dto==null){
            return null;
        }
        LigneCmndeClient lcc=new LigneCmndeClient();
        lcc.setId(dto.getId());
        lcc.setArticles(ArticleDto.toEntity(dto.getArticle()));
        lcc.setPrixUnitaire(dto.getPrixUnitaire());
        lcc.setQuantite(dto.getQuantite());
        return lcc;

    }
}
