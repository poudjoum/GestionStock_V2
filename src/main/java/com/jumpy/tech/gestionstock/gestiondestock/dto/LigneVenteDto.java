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
    /**
     * Le conditionnement de la ligne ; nul, elle est a l'unite de base. A l'ecriture, seul son
     * identifiant compte.
     */
    private ConditionnementDto conditionnement;
    /** Combien d'unites de base vaut une unite de la ligne. Calculee, jamais lue de la requete. */
    private BigDecimal contenance;
    /** Le lot scanne au comptoir (code GS1) : c'est lui qui sort, et non le premier perime. */
    private Long idLot;
    private ArticleDto article;
    private Long idEntreprise;

    public static LigneVenteDto fromEntity(LigneVente lgv) {
        if(lgv==null) {
            return null;
        }
        return LigneVenteDto.builder()
                .id(lgv.getId())
                .vente(VenteDto.fromEntity(lgv.getVente()))
                .quantite(lgv.getQuantite())
                .prixUnitaire(lgv.getPrixUnitaire())
                .conditionnement(ConditionnementDto.fromEntity(lgv.getConditionnement()))
                .contenance(lgv.getContenance())
                .article(ArticleDto.fromEntity(lgv.getArticles()))
                .idEntreprise(lgv.getIdEntreprise())
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
        lv.setIdEntreprise(dto.getIdEntreprise());
        return lv;
    }
}
