package com.jumpy.tech.gestionstock.gestiondestock.entities;

/**
 * De quoi une notification parle.
 *
 * Le type sert au front a choisir une icone et un regroupement, et a nous a retrouver une famille
 * d'alertes sans lire les titres — qui sont du texte, donc traduisibles et modifiables.
 */
public enum TypeNotification {

    /** Un article est passe sous son seuil, a zero, ou sous zero. */
    STOCK_ALERTE,

    /** Une facture vient d'etre emise. */
    FACTURE_EMISE,

    /** Un compte vient d'etre ouvert. */
    COMPTE_CREE
}
