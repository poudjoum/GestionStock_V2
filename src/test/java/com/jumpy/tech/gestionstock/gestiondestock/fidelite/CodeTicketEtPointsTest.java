package com.jumpy.tech.gestionstock.gestiondestock.fidelite;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Le code imprime sous le QR, et ce que vaut un ticket en points. */
class CodeTicketEtPointsTest {

    private static final BigDecimal DIX_MILLE = new BigDecimal("10000");

    // --- Le code ------------------------------------------------------------------------------

    @Test
    void un_code_neuf_a_la_forme_attendue_et_ne_se_repete_pas() {
        Set<String> vus = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            String code = CodeTicket.nouveau();
            assertThat(code).hasSize(12).matches("[0-9A-HJKMNP-TV-Z]{12}");
            vus.add(code);
        }
        // Soixante bits : dix mille tirages sans une seule collision, ou le hasard est faux.
        assertThat(vus).hasSize(10_000);
    }

    @Test
    void un_code_recopie_a_la_main_se_relit() {
        // Minuscules, tirets, et les lettres qu'une imprimante thermique rend ambigues.
        assertThat(CodeTicket.lire("7k3m-9p2q-a4tz")).contains("7K3M9P2QA4TZ");
        assertThat(CodeTicket.lire("O1IL23456789")).contains("011123456789");
        assertThat(CodeTicket.lire(" 7K3M 9P2Q A4TZ ")).contains("7K3M9P2QA4TZ");
    }

    @Test
    void ce_qui_n_est_pas_un_code_est_ecarte() {
        assertThat(CodeTicket.lire(null)).isEmpty();
        assertThat(CodeTicket.lire("")).isEmpty();
        assertThat(CodeTicket.lire("7K3M9P2QA4T")).isEmpty();      // onze caracteres
        assertThat(CodeTicket.lire("7K3M9P2QA4TZZ")).isEmpty();    // treize
        assertThat(CodeTicket.lire("7K3M9P2QA4TU")).isEmpty();     // U n'est pas dans l'alphabet
        assertThat(CodeTicket.lire("../../etc/pwd")).isEmpty();
    }

    // --- Les points ---------------------------------------------------------------------------

    @Test
    void un_point_par_tranche_entiere_de_dix_mille() {
        assertThat(PointsFidelite.pour(new BigDecimal("25000"), true, DIX_MILLE)).isEqualTo(2);
        assertThat(PointsFidelite.pour(new BigDecimal("10000"), true, DIX_MILLE)).isEqualTo(1);
        assertThat(PointsFidelite.pour(new BigDecimal("19999.99"), true, DIX_MILLE)).isEqualTo(1);
        // En dessous de la premiere tranche, rien : les francs ne s'accumulent pas d'un ticket a
        // l'autre.
        assertThat(PointsFidelite.pour(new BigDecimal("9000"), true, DIX_MILLE)).isZero();
    }

    @Test
    void le_montant_pour_un_point_est_celui_du_commerce() {
        assertThat(PointsFidelite.pour(new BigDecimal("2500"), true, new BigDecimal("500"))).isEqualTo(5);
    }

    @Test
    void sans_programme_ou_sans_montant_un_ticket_ne_vaut_rien() {
        assertThat(PointsFidelite.pour(new BigDecimal("50000"), false, DIX_MILLE)).isZero();
        assertThat(PointsFidelite.pour(null, true, DIX_MILLE)).isZero();
        assertThat(PointsFidelite.pour(new BigDecimal("50000"), true, null)).isZero();
        assertThat(PointsFidelite.pour(new BigDecimal("50000"), true, BigDecimal.ZERO)).isZero();
    }
}
