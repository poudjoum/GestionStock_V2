package com.jumpy.tech.gestionstock.gestiondestock.conditionnement;

import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeCodeBarres;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeEanTest {

    @Test
    void la_cle_des_codes_du_commerce_se_verifie() {
        // Des codes imprimes sur de vrais produits.
        assertThat(CodeEan.cleValide("5449000000996")).isTrue();
        assertThat(CodeEan.cleValide("4006381333931")).isTrue();
        assertThat(CodeEan.cleValide("96385074")).isTrue();
        assertThat(CodeEan.cleValide("036000291452")).isTrue();
    }

    @Test
    void un_chiffre_mal_recopie_est_detecte() {
        assertThat(CodeEan.cleValide("5449000000997")).isFalse();
        assertThat(CodeEan.cleValide("5449000000969")).isFalse();
        assertThat(CodeEan.cleValide("54490000009A6")).isFalse();
    }

    @Test
    void la_symbologie_se_deduit_du_code() {
        assertThat(CodeEan.deviner("5449000000996")).isEqualTo(TypeCodeBarres.EAN13);
        assertThat(CodeEan.deviner("96385074")).isEqualTo(TypeCodeBarres.EAN8);
        assertThat(CodeEan.deviner("036000291452")).isEqualTo(TypeCodeBarres.UPCA);
        String carton = "1544900000099";
        assertThat(CodeEan.deviner(carton + CodeEan.cle(carton))).isEqualTo(TypeCodeBarres.ITF14);
        // Un code interne a le prefixe 2 : il ne designe aucun produit du commerce.
        String interne = "200000000001";
        assertThat(CodeEan.deviner(interne + CodeEan.cle(interne))).isEqualTo(TypeCodeBarres.INTERNE);
        assertThat(CodeEan.deviner("REF-CIMENT-50")).isEqualTo(TypeCodeBarres.CODE128);
    }

    @RepeatedTest(20)
    void un_code_interne_tire_au_hasard_est_un_ean13_valide() {
        String code = CodeEan.interneAuHasard();

        assertThat(code).hasSize(13).startsWith("20");
        assertThat(CodeEan.cleValide(code)).isTrue();
        assertThat(CodeEan.deviner(code)).isEqualTo(TypeCodeBarres.INTERNE);
    }
}
