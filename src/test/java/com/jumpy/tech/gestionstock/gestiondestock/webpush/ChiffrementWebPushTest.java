package com.jumpy.tech.gestionstock.gestiondestock.webpush;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le chiffrement d'un message Web Push (RFC 8291), contre l'exemple publie par la RFC elle-meme.
 *
 * Un chiffrement faux ne se voit pas a l'envoi : le service push accepte le corps sans pouvoir le
 * lire, et c'est le navigateur qui le jette en silence. Aucune notification n'arrive, et rien ne
 * dit pourquoi. Le seul moyen d'en etre sur est de reproduire au bit pres l'exemple de l'annexe A,
 * avec ses cles, son sel et son message.
 */
class ChiffrementWebPushTest {

    private static final Base64.Decoder B64 = Base64.getUrlDecoder();
    private static final Base64.Encoder B64E = Base64.getUrlEncoder().withoutPadding();

    // RFC 8291, annexe A.
    private static final String MESSAGE = "When I grow up, I want to be a watermelon";
    private static final String CLE_PUBLIQUE_NAVIGATEUR =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String SECRET_AUTH = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String CLE_PRIVEE_SERVEUR = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String CLE_PUBLIQUE_SERVEUR =
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String SEL = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String CORPS_ATTENDU =
            "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";

    @Test
    void reproduit_l_exemple_de_la_rfc_8291() {
        KeyPair serveur = new KeyPair(
                CourbeP256.clePublique(B64.decode(CLE_PUBLIQUE_SERVEUR)),
                CourbeP256.clePrivee(B64.decode(CLE_PRIVEE_SERVEUR)));

        byte[] corps = ChiffrementWebPush.chiffrer(
                MESSAGE.getBytes(StandardCharsets.UTF_8),
                B64.decode(CLE_PUBLIQUE_NAVIGATEUR),
                B64.decode(SECRET_AUTH),
                serveur,
                B64.decode(SEL));

        assertThat(B64E.encodeToString(corps)).isEqualTo(CORPS_ATTENDU);
    }

    @Test
    void deux_envois_du_meme_message_ne_se_ressemblent_pas() {
        KeyPair navigateur = CourbeP256.nouvellePaire();
        byte[] clePublique = CourbeP256.brute(navigateur.getPublic());
        byte[] secret = new byte[16];

        byte[] un = ChiffrementWebPush.chiffrer("bonjour".getBytes(StandardCharsets.UTF_8), clePublique, secret);
        byte[] deux = ChiffrementWebPush.chiffrer("bonjour".getBytes(StandardCharsets.UTF_8), clePublique, secret);

        // Sel et cle ephemere neufs a chaque envoi : sans cela, le service push verrait deux fois
        // le meme corps et saurait que c'est le meme message.
        assertThat(un).isNotEqualTo(deux);
    }
}
