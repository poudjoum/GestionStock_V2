package com.jumpy.tech.gestionstock.gestiondestock.dto;

import java.math.BigDecimal;

/**
 * Ce que l'editeur doit faire d'un commerce, lu dans son activite.
 *
 * L'ordre est celui de l'urgence : un commerce qui decroche se rappelle aujourd'hui, un qui
 * ralentit cette semaine, un qui n'a pas demarre s'accompagne ; un actif se laisse vivre.
 */
public enum SanteCommerce {
    /** Plus une vente depuis deux semaines, alors qu'il en faisait. */
    DECROCHE,
    /** Pas de vente depuis plus de trois jours, ou un mois bien en dessous du precedent. */
    RALENTIT,
    /** Inscrit, mais pas une seule vente : l'installation n'est pas allee au bout. */
    PAS_DEMARRE,
    ACTIF;

    private static final BigDecimal SEUIL_DE_BAISSE = new BigDecimal("0.6");

    public static SanteCommerce de(long ventesTotales, Long joursDepuisDerniereVente,
                                   BigDecimal chiffre30j, BigDecimal chiffre30jPrecedents) {
        if (ventesTotales == 0 || joursDepuisDerniereVente == null) {
            return PAS_DEMARRE;
        }
        if (joursDepuisDerniereVente > 14) {
            return DECROCHE;
        }
        boolean baisse = chiffre30jPrecedents.signum() > 0
                && chiffre30j.compareTo(chiffre30jPrecedents.multiply(SEUIL_DE_BAISSE)) < 0;
        if (joursDepuisDerniereVente > 3 || baisse) {
            return RALENTIT;
        }
        return ACTIF;
    }
}
