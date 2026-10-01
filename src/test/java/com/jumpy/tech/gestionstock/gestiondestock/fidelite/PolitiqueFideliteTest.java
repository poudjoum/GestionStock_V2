package com.jumpy.tech.gestionstock.gestiondestock.fidelite;

import com.jumpy.tech.gestionstock.gestiondestock.dto.PolitiqueFideliteDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PolitiqueFideliteTest {

    @Test
    void une_politique_partielle_est_valide() {
        assertThat(PolitiqueFidelite.erreurs(PolitiqueFideliteDto.builder().pointsMinimumBon(500).build())).isEmpty();
    }

    @Test
    void chaque_reglage_hors_bornes_est_nomme() {
        PolitiqueFideliteDto politique = PolitiqueFideliteDto.builder()
                .montantParPoint(BigDecimal.ZERO)
                .valeurPointFcfa(new BigDecimal("-1"))
                .pointsMinimumBon(0)
                .dureeValiditeBonJours(PolitiqueFidelite.DUREE_VALIDITE_MAX_JOURS + 1)
                .build();

        assertThat(PolitiqueFidelite.erreurs(politique)).hasSize(4);
    }

    @Test
    void le_bon_est_arrondi_au_franc_inferieur() {
        assertThat(PolitiqueFidelite.montantDuBon(1000, BigDecimal.ONE)).isEqualByComparingTo("1000");
        assertThat(PolitiqueFidelite.montantDuBon(3, new BigDecimal("0.5"))).isEqualByComparingTo("1");
    }
}
