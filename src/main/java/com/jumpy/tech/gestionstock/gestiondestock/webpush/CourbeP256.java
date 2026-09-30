package com.jumpy.tech.gestionstock.gestiondestock.webpush;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;

/**
 * Les cles de la courbe P-256, sous la forme brute qu'emploie Web Push.
 *
 * Le navigateur donne sa cle publique en 65 octets — `04`, puis X et Y sur 32 octets chacun — et
 * les cles VAPID circulent sous la meme forme. Java, lui, ne connait que des objets `ECPublicKey`.
 * Cette classe fait la traduction dans les deux sens, avec le seul JDK.
 */
public final class CourbeP256 {

    private static final ECParameterSpec PARAMETRES = parametres();

    private CourbeP256() {
    }

    public static PublicKey clePublique(byte[] brute) {
        if (brute.length != 65 || brute[0] != 0x04) {
            throw new IllegalArgumentException("Une clé publique P-256 brute fait 65 octets et commence par 04");
        }
        byte[] x = new byte[32];
        byte[] y = new byte[32];
        System.arraycopy(brute, 1, x, 0, 32);
        System.arraycopy(brute, 33, y, 0, 32);
        try {
            return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(
                    new ECPoint(new BigInteger(1, x), new BigInteger(1, y)), PARAMETRES));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Clé publique P-256 invalide", e);
        }
    }

    public static PrivateKey clePrivee(byte[] brute) {
        try {
            return KeyFactory.getInstance("EC").generatePrivate(
                    new ECPrivateKeySpec(new BigInteger(1, brute), PARAMETRES));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Clé privée P-256 invalide", e);
        }
    }

    /** La cle publique en 65 octets : `04`, X, Y. */
    public static byte[] brute(PublicKey cle) {
        ECPoint point = ((ECPublicKey) cle).getW();
        byte[] brute = new byte[65];
        brute[0] = 0x04;
        copierSur32(point.getAffineX(), brute, 1);
        copierSur32(point.getAffineY(), brute, 33);
        return brute;
    }

    /** La cle privee en 32 octets. */
    public static byte[] brute(PrivateKey cle) {
        byte[] brute = new byte[32];
        copierSur32(((ECPrivateKey) cle).getS(), brute, 0);
        return brute;
    }

    public static KeyPair nouvellePaire() {
        try {
            KeyPairGenerator generateur = KeyPairGenerator.getInstance("EC");
            generateur.initialize(new ECGenParameterSpec("secp256r1"));
            return generateur.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 indisponible dans ce JDK", e);
        }
    }

    /**
     * Un entier sur exactement 32 octets.
     *
     * `BigInteger.toByteArray` en rend 33 quand le bit de poids fort est a 1 — un octet de signe —
     * et moins de 32 quand l'entier commence par des zeros. Les deux arrivent une fois sur deux ou
     * sur deux cent cinquante-six : assez rarement pour passer les premiers essais.
     */
    private static void copierSur32(BigInteger entier, byte[] cible, int position) {
        byte[] octets = entier.toByteArray();
        int longueur = Math.min(octets.length, 32);
        System.arraycopy(octets, octets.length - longueur, cible, position + 32 - longueur, longueur);
    }

    private static ECParameterSpec parametres() {
        try {
            AlgorithmParameters parametres = AlgorithmParameters.getInstance("EC");
            parametres.init(new ECGenParameterSpec("secp256r1"));
            return parametres.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 indisponible dans ce JDK", e);
        }
    }
}
