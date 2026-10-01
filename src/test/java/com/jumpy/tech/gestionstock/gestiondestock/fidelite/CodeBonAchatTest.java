package com.jumpy.tech.gestionstock.gestiondestock.fidelite;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CodeBonAchatTest {

    @Test
    void un_code_bon_neuf_a_la_forme_attendue_et_ne_se_repete_pas() {
        Set<String> vus = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            String code = CodeBonAchat.nouveau();
            assertThat(code).matches("^BON-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$");
            vus.add(code);
        }
        assertThat(vus).hasSize(10_000);
    }

    @Test
    void un_code_bon_saisi_a_la_main_se_normalise() {
        // Minuscules, sans tirets, avec lettres ambigues
        assertThat(CodeBonAchat.normaliser("bon-7k3m-9p2q")).contains("BON-7K3M-9P2Q");
        assertThat(CodeBonAchat.normaliser("7k3m9p2q")).contains("BON-7K3M-9P2Q");
        assertThat(CodeBonAchat.normaliser(" BON - O1IL - 2345 ")).contains("BON-0111-2345");
    }

    @Test
    void ce_qui_n_est_pas_un_code_bon_est_ecarte() {
        assertThat(CodeBonAchat.normaliser(null)).isEmpty();
        assertThat(CodeBonAchat.normaliser("")).isEmpty();
        assertThat(CodeBonAchat.normaliser("BON-123")).isEmpty();
        assertThat(CodeBonAchat.normaliser("BON-12345-67890")).isEmpty();
        assertThat(CodeBonAchat.normaliser("BON-123U-4567")).isEmpty(); // U interdit
    }
}
