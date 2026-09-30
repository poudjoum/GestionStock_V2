package com.jumpy.tech.gestionstock.gestiondestock.webpush;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le jeton VAPID : ce qui prouve au service push que c'est bien ce serveur qui envoie.
 *
 * Un jeton mal forme est refuse par le service push d'un 403, et la notification ne part pas ; il
 * se verifie donc ici tel que le service le verifiera.
 */
class VapidTest {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final KeyPair paire = CourbeP256.nouvellePaire();
    private final Vapid vapid = new Vapid(
            B64.encodeToString(CourbeP256.brute(paire.getPublic())),
            B64.encodeToString(CourbeP256.brute(paire.getPrivate())),
            "mailto:exploitant@exemple.test");

    @Test
    void le_jeton_est_signe_par_la_cle_annoncee() throws Exception {
        String entete = vapid.entete(URI.create("https://fcm.googleapis.com/fcm/send/abc"), Instant.now());

        String jeton = entete.substring("vapid t=".length(), entete.indexOf(", k="));
        String[] parties = jeton.split("\\.");
        Signature verification = Signature.getInstance("SHA256withECDSAinP1363Format");
        verification.initVerify(CourbeP256.clePublique(B64D.decode(entete.substring(entete.indexOf("k=") + 2))));
        verification.update((parties[0] + "." + parties[1]).getBytes(StandardCharsets.US_ASCII));

        assertThat(verification.verify(B64D.decode(parties[2]))).isTrue();
        // R et S bout a bout : 64 octets, et non la forme DER que Java produit par defaut.
        assertThat(B64D.decode(parties[2])).hasSize(64);
    }

    @Test
    void l_audience_est_l_origine_du_service_push() {
        String entete = vapid.entete(URI.create("https://updates.push.services.mozilla.com/wpush/v2/xyz"),
                Instant.parse("2026-09-30T10:00:00Z"));

        String charge = new String(B64D.decode(entete.split("\\.")[1]), StandardCharsets.UTF_8);
        // L'origine seule : un chemin dans l'audience et le service refuse le jeton.
        assertThat(charge).contains("\"aud\":\"https://updates.push.services.mozilla.com\"");
        assertThat(charge).contains("\"sub\":\"mailto:exploitant@exemple.test\"");
        // Douze heures, sous le plafond de vingt-quatre.
        assertThat(charge).contains("\"exp\":" + Instant.parse("2026-09-30T22:00:00Z").getEpochSecond());
    }

    @Test
    void une_cle_publique_mal_formee_est_refusee_des_le_demarrage() {
        String fausse = B64.encodeToString(new byte[65]);

        assertThatThrownBy(() -> new Vapid(fausse,
                B64.encodeToString(CourbeP256.brute(paire.getPrivate())), "mailto:a@b.test"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
