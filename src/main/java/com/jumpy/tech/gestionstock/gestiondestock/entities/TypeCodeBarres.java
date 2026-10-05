package com.jumpy.tech.gestionstock.gestiondestock.entities;

/** La symbologie d'un code-barres, telle qu'on l'imprime ou qu'on la lit. */
public enum TypeCodeBarres {
    EAN13,
    EAN8,
    UPCA,
    /** Le GTIN-14 imprime sur les cartons et les palettes. */
    ITF14,
    CODE128,
    QR,
    /** Un EAN-13 tire par le magasin pour un produit qui n'en porte pas (prefixe 20 a 29). */
    INTERNE
}
