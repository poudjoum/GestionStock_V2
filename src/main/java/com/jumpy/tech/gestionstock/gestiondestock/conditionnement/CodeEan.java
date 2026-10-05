package com.jumpy.tech.gestionstock.gestiondestock.conditionnement;

import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeCodeBarres;

import java.security.SecureRandom;

/**
 * Les codes EAN, UPC et ITF-14 : leur cle de controle, et les codes internes qu'un magasin tire pour ses
 * produits sans etiquette.
 *
 * La cle se verifie a l'enregistrement : un chiffre mal recopie a la main donnerait un code que
 * la douchette ne lira jamais, et l'erreur ne se verrait qu'au comptoir.
 */
public final class CodeEan {

    private static final SecureRandom HASARD = new SecureRandom();

    private CodeEan() {
    }

    /**
     * La cle de controle des chiffres donnes (sans la cle) : la somme ponderee 3-1 depuis la
     * droite, completee a la dizaine. Le meme calcul vaut pour l'EAN-13, l'EAN-8, l'UPC-A et
     * l'ITF-14.
     */
    public static int cle(String chiffresSansCle) {
        int somme = 0;
        for (int i = 0; i < chiffresSansCle.length(); i++) {
            int chiffre = chiffresSansCle.charAt(chiffresSansCle.length() - 1 - i) - '0';
            somme += i % 2 == 0 ? chiffre * 3 : chiffre;
        }
        return (10 - somme % 10) % 10;
    }

    /** Le code est-il fait de chiffres, et sa derniere position est-elle la bonne cle ? */
    public static boolean cleValide(String code) {
        if (code == null || code.length() < 2 || !code.chars().allMatch(Character::isDigit)) {
            return false;
        }
        return cle(code.substring(0, code.length() - 1)) == code.charAt(code.length() - 1) - '0';
    }

    /**
     * La symbologie d'un code dont on ne l'a pas dite. Faute de mieux, CODE128 : il accepte tout
     * ce qu'une douchette peut rendre.
     */
    public static TypeCodeBarres deviner(String code) {
        if (cleValide(code)) {
            switch (code.length()) {
                case 13:
                    return estInterne(code) ? TypeCodeBarres.INTERNE : TypeCodeBarres.EAN13;
                case 8:
                    return TypeCodeBarres.EAN8;
                case 12:
                    return TypeCodeBarres.UPCA;
                case 14:
                    return TypeCodeBarres.ITF14;
                default:
                    break;
            }
        }
        return TypeCodeBarres.CODE128;
    }

    /** Longueur attendue d'un code de cette symbologie, ou 0 si elle est libre. */
    public static int longueur(TypeCodeBarres type) {
        switch (type) {
            case EAN13:
            case INTERNE:
                return 13;
            case EAN8:
                return 8;
            case UPCA:
                return 12;
            case ITF14:
                return 14;
            default:
                return 0;
        }
    }

    /**
     * Les prefixes 20 a 29 sont reserves par GS1 a la diffusion restreinte : un code qui en porte
     * un ne designe aucun produit du commerce, et ne peut donc pas entrer en collision avec
     * l'EAN d'un fabricant.
     */
    public static boolean estInterne(String code) {
        return code != null && code.length() == 13 && code.charAt(0) == '2';
    }

    /** Un EAN-13 interne neuf : « 20 », dix chiffres tires au hasard, et sa cle. */
    public static String interneAuHasard() {
        StringBuilder chiffres = new StringBuilder("20");
        for (int i = 0; i < 10; i++) {
            chiffres.append(HASARD.nextInt(10));
        }
        return chiffres.toString() + cle(chiffres.toString());
    }
}
