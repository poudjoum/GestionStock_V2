package com.jumpy.tech.gestionstock.gestiondestock.promotion;

import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeRemise;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class PrixPromotionnelTest {

    @Test
    void un_pourcentage_reduit_le_prix_hors_taxes() {
        assertThat(PrixPromotionnel.prix(new BigDecimal("5000"), TypeRemise.POURCENTAGE, new BigDecimal("20")))
                .isEqualByComparingTo("4000");
        assertThat(PrixPromotionnel.prix(new BigDecimal("999"), TypeRemise.POURCENTAGE, new BigDecimal("33")))
                .isEqualByComparingTo("669.33");
    }

    @Test
    void un_prix_fixe_ne_rend_jamais_l_article_plus_cher() {
        // L'article a baisse depuis que la campagne a ete preparee.
        assertThat(PrixPromotionnel.prix(new BigDecimal("4000"), TypeRemise.PRIX_FIXE, new BigDecimal("4500")))
                .isEqualByComparingTo("4000");
    }

    @Test
    void le_jour_est_celui_du_magasin_et_non_celui_du_serveur() {
        // 23 h 30 UTC le 15 : il est 0 h 30 le 16 a Douala.
        Calendrier douala = Calendrier.fixe(Instant.parse("2026-10-15T23:30:00Z"), ZoneId.of("Africa/Douala"));

        assertThat(douala.aujourdhui()).isEqualTo(LocalDate.of(2026, 10, 16));
    }
}
