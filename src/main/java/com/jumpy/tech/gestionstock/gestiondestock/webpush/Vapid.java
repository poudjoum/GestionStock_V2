package com.jumpy.tech.gestionstock.gestiondestock.webpush;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * L'identite de ce serveur aupres des services push : VAPID (RFC 8292).
 *
 * Chaque envoi porte un jeton signe avec la cle privee du serveur. Le service push le compare a la
 * cle publique que le navigateur a recue en s'abonnant : un tiers qui aurait vole l'adresse d'un
 * abonnement ne peut rien y envoyer sans cette cle. La meme paire doit donc servir toute la vie
 * des abonnements — la changer les rend tous caducs.
 */
public final class Vapid {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    /** Douze heures : le maximum admis est vingt-quatre, et une horloge qui derive ne doit pas y toucher. */
    private static final Duration VALIDITE = Duration.ofHours(12);

    private final byte[] clePublique;
    private final PrivateKey clePrivee;
    private final String sujet;

    /**
     * @param clePublique la cle publique brute, 65 octets en base64url — celle que le front donne
     *                    au navigateur pour s'abonner
     * @param clePrivee   la cle privee brute, 32 octets en base64url
     * @param sujet       un moyen de joindre l'exploitant, `mailto:` ou `https:` : le service push
     *                    s'en sert pour prevenir avant de bloquer un serveur qui envoie mal
     */
    public Vapid(String clePublique, String clePrivee, String sujet) {
        this.clePublique = Base64.getUrlDecoder().decode(clePublique.trim());
        this.clePrivee = CourbeP256.clePrivee(Base64.getUrlDecoder().decode(clePrivee.trim()));
        this.sujet = sujet;
        // Une cle publique mal recopiee dans le .env se decouvrirait sinon au premier envoi, bien
        // apres le demarrage.
        CourbeP256.clePublique(this.clePublique);
    }

    public String clePublique() {
        return B64.encodeToString(clePublique);
    }

    /** L'en-tete `Authorization` d'un envoi vers `adresse`. */
    public String entete(URI adresse, Instant maintenant) {
        String audience = adresse.getScheme() + "://" + adresse.getRawAuthority();
        String enTeteJwt = b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}");
        String charge = b64("{\"aud\":\"" + audience + "\",\"exp\":"
                + maintenant.plus(VALIDITE).getEpochSecond() + ",\"sub\":\"" + sujet + "\"}");
        String signe = enTeteJwt + "." + charge;
        return "vapid t=" + signe + "." + signer(signe) + ", k=" + clePublique();
    }

    private String signer(String texte) {
        try {
            // Le format P1363 — R et S bout a bout, 64 octets — est celui qu'attend un JWT. Le
            // format par defaut de Java est du DER, que les services push refusent.
            Signature ecdsa = Signature.getInstance("SHA256withECDSAinP1363Format");
            ecdsa.initSign(clePrivee);
            ecdsa.update(texte.getBytes(StandardCharsets.US_ASCII));
            return B64.encodeToString(ecdsa.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Signature VAPID impossible", e);
        }
    }

    private static String b64(String json) {
        return B64.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
