package com.jumpy.tech.gestionstock.gestiondestock.demarrage;

/** Les premiers pas d'un commerce, dans l'ordre ou on les conseille. */
public enum EtapeDemarrage {
    /** L'adresse du magasin, que le ticket imprime sous son nom. */
    IDENTITE,
    /** Au moins une categorie pour ranger le catalogue. */
    CATEGORIES,
    /** Au moins un article, saisi ou importe. */
    ARTICLES,
    /** Un premier mouvement de stock : inventaire d'ouverture, reception ou correction. */
    STOCK,
    /** Un compte de plus que le gerant. Facultative : beaucoup de commerces sont tenus seuls. */
    EQUIPE,
    /** Une premiere vente encaissee, et non annulee. */
    PREMIERE_VENTE
}
