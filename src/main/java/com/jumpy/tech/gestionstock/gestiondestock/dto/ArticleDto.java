package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.UniteMesure;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

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
    /** Quantite sous laquelle l'article est signale. Facultatif. */
    private BigDecimal seuilAlerte;
    private CategoryDto category;
    private Long idEntreprise;
    /** L'unite du stock. Absente a la modification, celle de l'article est gardee. */
    private UniteMesure uniteBase;
    /**
     * En lecture seulement, et seulement sur les routes du catalogue : ils se gerent sur
     * /articles/{id}/conditionnements et /articles/{id}/codes-barres. Nuls ailleurs — une ligne
     * de vente n'a pas a recharger tout le catalogue de l'article qu'elle cite.
     */
    private List<ConditionnementDto> conditionnements;
    private List<CodeBarresDto> codesBarres;

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
                .seuilAlerte(art.getSeuilAlerte())
                .idEntreprise(art.getIdEntreprise())
                .uniteBase(art.getUniteBase())
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
        art.setSeuilAlerte(dto.getSeuilAlerte());
        art.setIdEntreprise(dto.getIdEntreprise());
        art.setUniteBase(dto.getUniteBase() == null ? UniteMesure.PIECE : dto.getUniteBase());
        return art;
    }
}
