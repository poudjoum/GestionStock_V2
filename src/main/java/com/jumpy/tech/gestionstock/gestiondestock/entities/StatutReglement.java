package com.jumpy.tech.gestionstock.gestiondestock.entities;

/**
 * Ou en est une facture de son reglement.
 *
 * Deduit de la somme des reglements, jamais stocke : c'est un resultat de lecture, et le stocker
 * ouvrirait la porte a une facture marquee reglee dont les encaissements ne suivent pas.
 */
public enum StatutReglement {

    IMPAYEE,
    PARTIELLEMENT_REGLEE,
    REGLEE
}
