package com.jumpy.tech.gestionstock.gestiondestock.config.security.jwt;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le cycle complet d'un jeton : fabrication, verification, lecture.
 *
 * Rien ne couvrait cette classe, alors qu'elle porte toute l'authentification. La montee en
 * JJWT 0.13 a reecrit ses trois methodes — `setSubject` et `parserBuilder` ont disparu au profit
 * de `subject` et `parser`, et l'algorithme se deduit desormais de la cle. Une erreur de
 * transcription ne se serait vue qu'a la premiere connexion reelle.
 */
class JwtUtilsTest {

    // 64 octets en base64 : la taille impose HS512, et c'est ce que fait la production.
    private static final String SECRET =
            "dGVzdC1zZWNyZXQtZGUtc2lnbmF0dXJlLXBvdXItbGVzLXRlc3RzLXVuaXF1ZW1lbnQtMDEyMzQ1Njc4OQ==";

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwrSecret", SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 3_600_000);
    }

    private Authentication authentificationDe(String username) {
        UserDetailsImpl principal = new UserDetailsImpl(1L, username, username + "@exemple.test",
                "peu-importe", 7L, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void un_jeton_emis_est_valide_et_porte_son_titulaire() {
        String jeton = jwtUtils.generateJwtToken(authentificationDe("gerant"));

        assertThat(jwtUtils.validateJwtToken(jeton)).isTrue();
        assertThat(jwtUtils.getUserNameFromJwtToken(jeton)).isEqualTo("gerant");
    }

    @Test
    void un_jeton_dont_la_charge_utile_a_ete_modifiee_est_rejete() {
        String jeton = jwtUtils.generateJwtToken(authentificationDe("caissier"));

        // On remplace un caractere de la charge utile : la signature ne correspond plus.
        String[] parties = jeton.split("\\.");
        char premier = parties[1].charAt(0);
        parties[1] = (premier == 'A' ? 'B' : 'A') + parties[1].substring(1);
        String falsifie = String.join(".", parties);

        assertThat(jwtUtils.validateJwtToken(falsifie)).isFalse();
    }

    @Test
    void un_jeton_signe_avec_une_autre_cle_est_rejete() {
        JwtUtils autreEmetteur = new JwtUtils();
        ReflectionTestUtils.setField(autreEmetteur, "jwrSecret",
                "dW5lLWF1dHJlLWNsZS1jb21wbGV0ZW1lbnQtZGlmZmVyZW50ZS1wb3VyLWxlLXRlc3QtMDEyMzQ1Njc4OQ==");
        ReflectionTestUtils.setField(autreEmetteur, "jwtExpirationMs", 3_600_000);

        String jetonEtranger = autreEmetteur.generateJwtToken(authentificationDe("intrus"));

        assertThat(jwtUtils.validateJwtToken(jetonEtranger)).isFalse();
    }

    @Test
    void un_jeton_expire_est_rejete() {
        // Duree negative : le jeton nait expire, sans qu'il faille attendre.
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", -1000);

        String perime = jwtUtils.generateJwtToken(authentificationDe("gerant"));

        assertThat(jwtUtils.validateJwtToken(perime)).isFalse();
    }

    @Test
    void une_chaine_qui_n_est_pas_un_jeton_est_rejetee() {
        assertThat(jwtUtils.validateJwtToken("pas-du-tout-un-jeton")).isFalse();
        assertThat(jwtUtils.validateJwtToken("")).isFalse();
    }
}
