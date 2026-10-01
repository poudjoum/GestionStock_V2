package com.jumpy.tech.gestionstock.gestiondestock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Une campagne telle que la voit le client dans l'application : le magasin, le message, et les
 * prix tels qu'il les paiera — toutes taxes comprises.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampagnePubliqueDto {
    private Long id;
    private String titre;
    private String message;
    private String image;
    private LocalDate dateDebut;
    private LocalDate dateFin;

    private Long idMagasin;
    private String nomMagasin;
    private String villeMagasin;
    private String logoMagasin;

    private List<Article> articles;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Article {
        private Long id;
        private String designation;
        private String photo;
        private BigDecimal prixNormalTtc;
        private BigDecimal prixPromoTtc;
        /** La reduction en pour cent, arrondie : ce qu'on affiche en gros sur l'etiquette. */
        private int remisePourcent;
    }
}
