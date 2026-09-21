package com.jumpy.tech.gestionstock.gestiondestock.entities;

/**
 * Ou en est un message qui doit quitter l'application.
 *
 * `ABANDONNE` n'est pas une suppression : le message reste lisible avec sa derniere erreur. Une
 * facture qui n'est jamais partie est une question qu'on se posera, et effacer la ligne
 * effacerait la reponse.
 */
public enum EtatEnvoi {

    A_ENVOYER,
    ENVOYE,
    ABANDONNE
}
