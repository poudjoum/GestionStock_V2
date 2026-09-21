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
    /** Ce qui est deja arrive. */
    private BigDecimal quantiteLivree;
    /** Ce qui reste attendu : quantite commandee moins quantite livree. */
    private BigDecimal resteALivrer;
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
                .quantiteLivree(ligneCmndeFour.getQuantiteLivree())
                .resteALivrer(reste(ligneCmndeFour.getQuantite(), ligneCmndeFour.getQuantiteLivree()))
                .prixUnitaire(ligneCmndeFour.getPrixUnitaire())
                .build();
    }

    /** Jamais negatif : une ligne ne peut pas « rester a livrer » moins que rien. */
    private static BigDecimal reste(BigDecimal commandee, BigDecimal livree) {
        if (commandee == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal deja = livree == null ? BigDecimal.ZERO : livree;
        return commandee.subtract(deja).max(BigDecimal.ZERO);
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

