package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneTransfert;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Une ligne de transfert. A l'ecriture, seuls l'article, le conditionnement (son identifiant) et
 * la quantite comptent ; la contenance se fige cote serveur.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LigneTransfertDto {
    private Long id;
    private ArticleDto article;
    private ConditionnementDto conditionnement;
    private BigDecimal contenance;
    private BigDecimal quantite;
    private BigDecimal quantiteRecue;
    private String motifEcart;

    public static LigneTransfertDto fromEntity(LigneTransfert ligne) {
        if (ligne == null) {
            return null;
        }
        return LigneTransfertDto.builder()
                .id(ligne.getId())
                .article(ArticleDto.fromEntity(ligne.getArticle()))
                .conditionnement(ConditionnementDto.fromEntity(ligne.getConditionnement()))
                .contenance(ligne.getContenance())
                .quantite(ligne.getQuantite())
                .quantiteRecue(ligne.getQuantiteRecue())
                .motifEcart(ligne.getMotifEcart())
                .build();
    }
}
