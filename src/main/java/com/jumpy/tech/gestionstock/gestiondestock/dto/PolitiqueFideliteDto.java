package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Entreprise;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * La politique de fidelite d'un magasin : ce que rapporte un achat, et ce que valent les points.
 *
 * Quatre reglages, et un interrupteur. Le premier se lit au comptoir — il est imprime sur le
 * ticket —, les trois autres dans l'application du client, au moment d'echanger.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolitiqueFideliteDto {

    /** Si les tickets rapportent des points. Les points deja gagnes restent echangeables. */
    private Boolean fideliteActive;
    /** Ce qu'il faut payer, TTC et sur un seul ticket, pour gagner un point. */
    private BigDecimal montantParPoint;
    /** Ce que vaut un point, en francs, une fois echange contre un bon. */
    private BigDecimal valeurPointFcfa;
    /** Le moins de points qu'on puisse echanger d'un coup. */
    private Integer pointsMinimumBon;
    /** Combien de jours un bon reste valable apres son emission. */
    private Integer dureeValiditeBonJours;

    public static PolitiqueFideliteDto de(Entreprise entreprise) {
        return PolitiqueFideliteDto.builder()
                .fideliteActive(entreprise.isFideliteActive())
                .montantParPoint(entreprise.getMontantParPoint())
                .valeurPointFcfa(entreprise.getValeurPointFcfa())
                .pointsMinimumBon(entreprise.getPointsMinimumBon())
                .dureeValiditeBonJours(entreprise.getDureeValiditeBonJours())
                .build();
    }
}
