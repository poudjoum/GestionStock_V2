package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneComptage;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un article dans une seance de comptage, tel que l'ecran le montre.
 *
 * Deux nombres qui se ressemblent et ne disent pas la meme chose :
 *
 * `ecart` confronte ce qu'on a compte a ce que le logiciel croyait avoir en ouvrant la seance.
 * C'est celui qu'on regarde : il repond a « combien en manque-t-il », et il ne bouge pas pendant
 * qu'on compte.
 *
 * `correction` est ce que la validation posera reellement en mouvement de stock. Les deux sont
 * egaux tant que rien ne bouge pendant le comptage. Ils different des qu'une vente passe au
 * comptoir entre l'ouverture et le comptage de cette ligne-la — et c'est la correction qui a
 * raison, puisqu'elle vise le stock tel qu'il etait au moment ou l'on a regarde l'etagere.
 */
public record LigneComptageDto(
        Long id,
        Long idArticle,
        String codeArticle,
        String designation,
        BigDecimal quantiteTheorique,
        BigDecimal quantiteComptee,
        BigDecimal ecart,
        BigDecimal correction,
        Instant compteLe) {

    public static LigneComptageDto fromEntity(LigneComptage ligne) {
        if (ligne == null) {
            return null;
        }
        BigDecimal comptee = ligne.getQuantiteComptee();
        return new LigneComptageDto(
                ligne.getId(),
                ligne.getArticle() == null ? null : ligne.getArticle().getId(),
                ligne.getCodeArticle(),
                ligne.getDesignation(),
                ligne.getQuantiteTheorique(),
                comptee,
                comptee == null ? null : comptee.subtract(ligne.getQuantiteTheorique()),
                comptee == null ? null : comptee.subtract(ligne.getStockAuComptage()),
                ligne.getCompteLe());
    }
}
