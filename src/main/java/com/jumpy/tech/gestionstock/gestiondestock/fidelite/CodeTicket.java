package com.jumpy.tech.gestionstock.gestiondestock.fidelite;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Le code imprime sous le QR d'un ticket.
 *
 * Douze caracteres de l'alphabet de Crockford — chiffres et majuscules, sans I, L, O ni U — soit
 * soixante bits tires au hasard. Assez pour qu'on ne puisse pas deviner le ticket d'un autre, et
 * assez court pour se recopier a la main quand le QR est tache ou dechire.
 *
 * L'alphabet sans lettres ambigues vaut pour cette recopie : un client lit « 0 » ou « O », « 1 »
 * ou « I » selon la police de l'imprimante. La lecture les confond donc volontairement.
 */
public final class CodeTicket {

    public static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    public static final int LONGUEUR = 12;
    private static final Pattern FORME = Pattern.compile("[0-9A-HJKMNP-TV-Z]{" + LONGUEUR + "}");
    private static final SecureRandom HASARD = new SecureRandom();

    private CodeTicket() {
    }

    /** Un code neuf. Chaque caractere tire cinq bits : 256 est un multiple de 32, sans biais. */
    public static String nouveau() {
        byte[] octets = new byte[LONGUEUR];
        HASARD.nextBytes(octets);
        StringBuilder code = new StringBuilder(LONGUEUR);
        for (byte octet : octets) {
            code.append(ALPHABET.charAt(octet & 31));
        }
        return code.toString();
    }

    /**
     * Le code tel qu'il est range, a partir de ce qu'on a lu ou tape : majuscules, sans espaces ni
     * tirets, O lu comme 0 et I ou L comme 1. Vide si ce n'est pas un code de ticket.
     */
    public static Optional<String> lire(String saisi) {
        if (saisi == null) {
            return Optional.empty();
        }
        String code = saisi.trim().toUpperCase()
                .replace("-", "").replace(" ", "")
                .replace('O', '0').replace('I', '1').replace('L', '1');
        return FORME.matcher(code).matches() ? Optional.of(code) : Optional.empty();
    }
}
