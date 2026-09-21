package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mouvements de stock.
 *
 * Cette couche manquait entierement : l'entite MvtStk et son repository existaient depuis le
 * premier commit, sans que rien ne les ecrive ni ne les lise. Une vente ne retirait donc aucune
 * quantite du magasin, et le stock disponible d'un article n'etait calcule nulle part.
 */
public interface MvtStkService {

    /** Entrees moins sorties. Zero si l'article n'a jamais bouge. */
    BigDecimal stockReelArticle(Long idArticle);

    /** L'historique d'un article, du plus recent au plus ancien. */
    List<MvtStkDto> mvtStkArticle(Long idArticle);

    MvtStkDto entreeStock(MvtStkDto dto);

    /**
     * Refuse la sortie si elle depasse le stock reel : accepter revient a inscrire en magasin une
     * quantite negative, que le comptoir decouvrirait en cherchant une marchandise absente.
     */
    MvtStkDto sortieStock(MvtStkDto dto);
}
