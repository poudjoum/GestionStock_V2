package com.jumpy.tech.gestionstock.gestiondestock.dto;

import java.math.BigDecimal;

/**
 * La plateforme en cinq nombres : ce que l'editeur vient lire en premier.
 *
 * `echus` et `suspendus` sont separes parce qu'ils appellent deux gestes differents : le premier
 * demande une relance, le second est deja une decision prise.
 */
public record ResumePlateformeDto(
        long commerces,
        long actifs,
        long echus,
        long suspendus,
        /** Les abonnements qui arrivent a echeance dans le mois : ceux qu'il faut relancer. */
        long aRelancer,
        long comptes,
        long ventes,
        BigDecimal chiffreFacture,
        /** L'activite : ceux qui vendent, ceux qui ralentissent, ceux qui decrochent. */
        long enActivite,
        long ralentissent,
        long decrochent,
        long pasDemarres,
        /** Le chiffre d'affaires HT de toute la plateforme sur 30 jours. */
        BigDecimal chiffre30j) {
}
