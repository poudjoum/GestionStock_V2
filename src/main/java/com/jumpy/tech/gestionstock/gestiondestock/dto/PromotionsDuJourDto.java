package com.jumpy.tech.gestionstock.gestiondestock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Ce que la caisse garde de la journee : les campagnes en cours, et leurs articles en promotion.
 *
 * Les campagnes a part des promotions : une campagne peut n'etre qu'un message, sans article
 * reduit, et ses tickets rapportent quand meme des points — le ticket imprime doit le dire.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionsDuJourDto {

    private List<Campagne> campagnes;
    private List<PromotionArticleDto> promotions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Campagne {
        private Long id;
        private String titre;
        private LocalDate dateDebut;
        private LocalDate dateFin;
    }
}
