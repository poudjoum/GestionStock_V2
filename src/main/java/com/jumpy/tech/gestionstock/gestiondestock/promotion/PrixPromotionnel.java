package com.jumpy.tech.gestionstock.gestiondestock.promotion;

import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeRemise;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Le prix d'un article en promotion.
 *
 * La regle est ici et nulle part ailleurs cote serveur : la caisse, la page de la campagne et
 * l'application du client doivent annoncer le meme prix. Le front la recopie a l'identique — il
 * vend aussi hors ligne.
 *
 * Le prix est hors taxes, comme celui de l'article : la TVA s'y ajoute ensuite, a la facture.
 */
public final class PrixPromotionnel {

    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    private PrixPromotionnel() {
    }

    /**
     * Le prix reduit. Jamais plus cher que le prix normal : un « prix fixe » saisi au-dessus du
     * prix de l'article — qui a pu baisser depuis — n'augmente rien.
     */
    public static BigDecimal prix(BigDecimal prixNormalHt, TypeRemise type, BigDecimal valeur) {
        if (prixNormalHt == null) {
            return null;
        }
        BigDecimal reduit = switch (type) {
            case POURCENTAGE -> prixNormalHt.multiply(CENT.subtract(valeur))
                    .divide(CENT, 2, RoundingMode.HALF_UP);
            case PRIX_FIXE -> valeur;
        };
        return reduit.min(prixNormalHt);
    }
}
