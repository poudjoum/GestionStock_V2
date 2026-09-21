package com.jumpy.tech.gestionstock.gestiondestock.entities;

/**
 * L'etat d'un article au regard de son seuil.
 *
 * `SANS_SEUIL` n'est pas un defaut : beaucoup d'articles n'ont pas a etre surveilles, et les
 * confondre avec ceux qui vont bien ferait croire a une surveillance qui n'existe pas.
 */
public enum StatutStock {

    /**
     * Moins que rien en magasin.
     *
     * Distinct de `RUPTURE`, et pas par gout du detail : les deux n'appellent pas le meme geste.
     * Une rupture se commande au fournisseur ; un stock negatif se compte sur l'etagere. Il
     * signifie qu'il est sorti plus de marchandise que le magasin n'en avait recu — le cas
     * ordinaire etant deux caisses qui vendent hors ligne le dernier exemplaire.
     */
    NEGATIF,
    RUPTURE,
    SOUS_SEUIL,
    SUFFISANT,
    SANS_SEUIL
}
