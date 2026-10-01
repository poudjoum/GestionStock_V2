package com.jumpy.tech.gestionstock.gestiondestock.fidelite;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Generation et validation des codes de bons d'achat.
 *
 * Format lisible : BON-XXXX-XXXX (alphabet de Crockford, 40 bits d'entropie).
 * Facile a afficher sous forme de QR code ou a recopier par un caissier.
 */
public final class CodeBonAchat {

    public static final String PREFIXE = "BON-";
    public static final int LONGUEUR_CORPS = 8;
    private static final Pattern FORME = Pattern.compile("^BON-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$");
    private static final SecureRandom HASARD = new SecureRandom();

    private CodeBonAchat() {
    }

    public static String nouveau() {
        byte[] octets = new byte[LONGUEUR_CORPS];
        HASARD.nextBytes(octets);
        StringBuilder sb = new StringBuilder(PREFIXE);
        for (int i = 0; i < LONGUEUR_CORPS; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(CodeTicket.ALPHABET.charAt(octets[i] & 31));
        }
        return sb.toString();
    }

    public static Optional<String> normaliser(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String brut = code.trim().toUpperCase().replace(" ", "").replace("-", "");
        if (brut.startsWith("BON")) {
            brut = brut.substring(3);
        }
        String corps = brut.replace('O', '0').replace('I', '1').replace('L', '1');
        if (corps.length() != LONGUEUR_CORPS) {
            return Optional.empty();
        }
        String reconstruit = PREFIXE + corps.substring(0, 4) + "-" + corps.substring(4, 8);
        return FORME.matcher(reconstruit).matches() ? Optional.of(reconstruit) : Optional.empty();
    }
}
