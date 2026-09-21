package com.jumpy.tech.gestionstock.gestiondestock.entities;

/**
 * L'etat d'un article au regard de son seuil.
 *
 * `SANS_SEUIL` n'est pas un defaut : beaucoup d'articles n'ont pas a etre surveilles, et les
 * confondre avec ceux qui vont bien ferait croire a une surveillance qui n'existe pas.
 */
public enum StatutStock {

    RUPTURE,
    SOUS_SEUIL,
    SUFFISANT,
    SANS_SEUIL
}
