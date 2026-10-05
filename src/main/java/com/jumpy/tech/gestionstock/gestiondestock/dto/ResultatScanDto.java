package com.jumpy.tech.gestionstock.gestiondestock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ce que designe un code scanne : un article, et le conditionnement presente.
 *
 * `conditionnement` est nul quand le code est celui de l'unite de base — ou le code de l'article
 * lui-meme, qui reste reconnu pour les produits qu'on n'a pas encore etiquetes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultatScanDto {
    private ArticleDto article;
    private ConditionnementDto conditionnement;
}
