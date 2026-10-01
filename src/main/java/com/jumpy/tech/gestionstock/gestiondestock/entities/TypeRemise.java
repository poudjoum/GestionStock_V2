package com.jumpy.tech.gestionstock.gestiondestock.entities;

/** Comment un article en promotion est reduit : d'un pourcentage, ou a un prix fixe. */
public enum TypeRemise {
    /** `valeur` est un pourcentage du prix, strictement entre 0 et 100. */
    POURCENTAGE,
    /** `valeur` est le prix hors taxes pendant la campagne. */
    PRIX_FIXE
}
