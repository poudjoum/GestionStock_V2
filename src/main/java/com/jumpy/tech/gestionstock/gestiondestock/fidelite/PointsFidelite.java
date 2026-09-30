package com.jumpy.tech.gestionstock.gestiondestock.fidelite;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Ce que vaut un ticket en points.
 *
 * Un point par tranche entiere de `montantParPoint` payee TTC, sur ce seul ticket : 25 000 F donnent
 * deux points a 10 000 F le point, 9 000 F n'en donnent aucun. Le TTC, parce que c'est ce que le
 * client a sorti de sa poche ; par ticket, parce que c'est ce que le caissier peut annoncer sans
 * connaitre le client.
 *
 * La regle est ici, et nulle part ailleurs cote serveur : le ticket imprime, la page du QR et, plus
 * tard, le jeu qui creditera ces points doivent tous dire le meme nombre.
 */
public final class PointsFidelite {

    private PointsFidelite() {
    }

    public static int pour(BigDecimal totalTtc, boolean fideliteActive, BigDecimal montantParPoint) {
        if (!fideliteActive || totalTtc == null || montantParPoint == null || montantParPoint.signum() <= 0) {
            return 0;
        }
        return totalTtc.divide(montantParPoint, 0, RoundingMode.DOWN).max(BigDecimal.ZERO).intValue();
    }
}
