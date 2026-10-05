package com.jumpy.tech.gestionstock.gestiondestock.entities;

/** Ce qu'un site fait de sa marchandise. */
public enum TypeSite {
    /** Il vend : il a une caisse, et ses clients. */
    MAGASIN,
    /** Il stocke et livre : les magasins, par transfert, et leurs clients, sur commande. Il ne vend pas. */
    ENTREPOT
}
