package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import org.springframework.util.StringUtils;

/**
 * Ce qu'on fait d'un terme de recherche avant de l'envoyer en base.
 *
 * Un champ de recherche vide n'arrive pas absent : le navigateur envoie `?q=`. Les espaces de
 * bord, eux, viennent du clavier d'un telephone, qui en ajoute un apres chaque mot.
 *
 * La chaine vide, et non `null`, represente « pas de filtre ». Ce n'est pas un gout : un
 * parametre nul arrive en base sans type, et PostgreSQL, voyant `lower($1)`, doit choisir entre
 * `lower(text)` et `lower(bytea)` — il prend le second et la requete echoue sur
 * « function lower(bytea) does not exist ». Une chaine vide est liee comme du texte, et la
 * question ne se pose plus.
 */
final class RechercheUtils {

    /** « Pas de filtre ». Les requetes la reconnaissent par `:q = ''`. */
    static final String AUCUN_FILTRE = "";

    private RechercheUtils() {
    }

    /** Rend la chaine vide pour tout ce qui ne filtre rien : absent, vide, ou que des espaces. */
    static String normaliser(String q) {
        if (!StringUtils.hasText(q)) {
            return AUCUN_FILTRE;
        }
        return q.trim();
    }
}
