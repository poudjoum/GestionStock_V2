package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.PromotionArticle;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeRemise;
import com.jumpy.tech.gestionstock.gestiondestock.promotion.PrixPromotionnel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un article en promotion. A l'ecriture, seuls `idArticle`, `typeRemise` et `valeur` comptent ;
 * le reste est rendu pour l'affichage, et pour la caisse qui vend hors ligne.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionArticleDto {
    private Long idArticle;
    private TypeRemise typeRemise;
    private BigDecimal valeur;

    private String codeArticle;
    private String designation;
    private String photo;
    /** Le prix normal, hors taxes. */
    private BigDecimal prixNormalHt;
    /** Le prix pendant la campagne, hors taxes. */
    private BigDecimal prixPromoHt;
    private BigDecimal tauxTva;

    /** La campagne qui le porte : la caisse en a besoin pour savoir jusqu'a quand. */
    private Long idCampagne;
    private String titreCampagne;
    private LocalDate dateDebut;
    private LocalDate dateFin;

    public static PromotionArticleDto de(PromotionArticle promotion) {
        Article article = promotion.getArticle();
        return PromotionArticleDto.builder()
                .idArticle(article.getId())
                .typeRemise(promotion.getTypeRemise())
                .valeur(promotion.getValeur())
                .codeArticle(article.getCodeArticle())
                .designation(article.getDesignation())
                .photo(article.getPhoto())
                .prixNormalHt(article.getPrixUnitaire())
                .prixPromoHt(PrixPromotionnel.prix(article.getPrixUnitaire(), promotion.getTypeRemise(), promotion.getValeur()))
                .tauxTva(article.getTauxTva())
                .idCampagne(promotion.getCampagne().getId())
                .titreCampagne(promotion.getCampagne().getTitre())
                .dateDebut(promotion.getCampagne().getDateDebut())
                .dateFin(promotion.getCampagne().getDateFin())
                .build();
    }
}
