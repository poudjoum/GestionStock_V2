package com.jumpy.tech.gestionstock.gestiondestock.webpush;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;

/**
 * Le chiffrement d'un message Web Push : RFC 8291, sur le codage `aes128gcm` de la RFC 8188.
 *
 * Le message passe par le service push du navigateur — Google, Mozilla, Apple, Microsoft — et ce
 * service ne doit pas pouvoir le lire : une alerte de stock dit ce que vend un commerce et ce qui
 * lui manque. Il est donc chiffre ici pour le seul navigateur abonne, avec la cle publique et le
 * secret que celui-ci a donnes en s'abonnant.
 *
 * Avec le seul JDK — ECDH, HMAC, AES-GCM —, et non une bibliotheque : celles qui existent en Java
 * ne sont plus entretenues et tirent BouncyCastle et un client HTTP de plus. La justesse est
 * verifiee contre l'exemple publie par la RFC (`ChiffrementWebPushTest`).
 */
public final class ChiffrementWebPush {

    /** La taille d'enregistrement annoncee. Un message de notification tient toujours dans un seul. */
    private static final int TAILLE_ENREGISTREMENT = 4096;
    private static final SecureRandom HASARD = new SecureRandom();

    private ChiffrementWebPush() {
    }

    /**
     * Chiffre `message` pour le navigateur qui a donne `clePubliqueNavigateur` (65 octets) et
     * `secretAuth` (16 octets). Une cle ephemere et un sel neufs a chaque appel.
     */
    public static byte[] chiffrer(byte[] message, byte[] clePubliqueNavigateur, byte[] secretAuth) {
        byte[] sel = new byte[16];
        HASARD.nextBytes(sel);
        return chiffrer(message, clePubliqueNavigateur, secretAuth, CourbeP256.nouvellePaire(), sel);
    }

    /** La meme chose, avec la cle ephemere et le sel imposes : c'est ce que fait le test de la RFC. */
    static byte[] chiffrer(byte[] message, byte[] clePubliqueNavigateur, byte[] secretAuth,
                           KeyPair serveur, byte[] sel) {
        try {
            byte[] clePubliqueServeur = CourbeP256.brute(serveur.getPublic());

            // Le secret partage, que seuls ce serveur et ce navigateur savent calculer.
            KeyAgreement ecdh = KeyAgreement.getInstance("ECDH");
            ecdh.init(serveur.getPrivate());
            ecdh.doPhase(CourbeP256.clePublique(clePubliqueNavigateur), true);
            byte[] secretEcdh = ecdh.generateSecret();

            // Melange avec le secret d'authentification du navigateur, et lie aux deux cles
            // publiques : un tiers qui intercepterait l'une ne pourrait rien rejouer.
            byte[] infoCle = concat(
                    "WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[]{0},
                    clePubliqueNavigateur, clePubliqueServeur);
            byte[] ikm = hkdf(secretAuth, secretEcdh, infoCle, 32);

            byte[] cleContenu = hkdf(sel, ikm, info("Content-Encoding: aes128gcm"), 16);
            byte[] nonce = hkdf(sel, ikm, info("Content-Encoding: nonce"), 12);

            // Un seul enregistrement, donc le dernier : il se termine par le delimiteur 0x02.
            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cleContenu, "AES"),
                    new GCMParameterSpec(128, nonce));
            byte[] chiffre = aes.doFinal(concat(message, new byte[]{2}));

            // L'en-tete de la RFC 8188 : sel, taille d'enregistrement, et la cle publique du
            // serveur, dont le navigateur a besoin pour refaire le calcul.
            ByteBuffer entete = ByteBuffer.allocate(16 + 4 + 1 + clePubliqueServeur.length);
            entete.put(sel).putInt(TAILLE_ENREGISTREMENT)
                    .put((byte) clePubliqueServeur.length).put(clePubliqueServeur);
            return concat(entete.array(), chiffre);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Chiffrement Web Push impossible", e);
        }
    }

    private static byte[] info(String libelle) {
        return concat(libelle.getBytes(StandardCharsets.US_ASCII), new byte[]{0});
    }

    /** HKDF-SHA256 (RFC 5869), pour une longueur d'au plus 32 octets : un seul tour suffit. */
    private static byte[] hkdf(byte[] sel, byte[] ikm, byte[] info, int longueur)
            throws GeneralSecurityException {
        byte[] prk = hmac(sel, ikm);
        byte[] t1 = hmac(prk, concat(info, new byte[]{1}));
        byte[] sortie = new byte[longueur];
        System.arraycopy(t1, 0, sortie, 0, longueur);
        return sortie;
    }

    private static byte[] hmac(byte[] cle, byte[] donnees) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(cle, "HmacSHA256"));
        return mac.doFinal(donnees);
    }

    private static byte[] concat(byte[]... morceaux) {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        for (byte[] morceau : morceaux) {
            sortie.writeBytes(morceau);
        }
        return sortie.toByteArray();
    }
}
