package com.jumpy.tech.gestionstock.gestiondestock.entities;

/**
 * Ou en est l'abonnement d'un commerce.
 *
 * Il ne se stocke pas : il se deduit de l'echeance et de la suspension, a l'instant ou on le
 * demande. Une colonne qu'il faudrait tenir a jour se desynchroniserait le jour ou personne ne
 * passe la mettre a jour — et ce jour-la, un abonnement echu resterait « actif » pour toujours.
 */
public enum StatutAbonnement {

    /** L'echeance est devant nous, ou il n'y a pas d'abonnement a faire respecter. */
    ACTIF,

    /** L'echeance est passee : les comptes de ce commerce n'entrent plus. */
    ECHU,

    /** Ferme a la main par l'editeur, quelle que soit l'echeance. */
    SUSPENDU
}
