package com.jumpy.tech.gestionstock.gestiondestock.entities;

/**
 * Pourquoi une quantite a bouge.
 *
 * Le sens seul ne suffit plus des lors qu'une vente peut etre corrigee : deux entrees de 15 sur
 * le meme article, l'une venant d'une livraison et l'autre de l'annulation d'une vente, sont
 * indiscernables sans ce motif — et c'est justement ce qu'on cherche a comprendre quand un stock
 * ne tombe pas juste.
 */
public enum MotifMvtStk {

    /** Livraison d'une commande fournisseur. */
    LIVRAISON_COMMANDE,

    /** Sortie constatee par une vente. */
    VENTE,

    /** Retour en magasin d'une vente annulee. */
    ANNULATION_VENTE,

    /** Ajustement d'une ligne de vente deja enregistree, dans un sens ou dans l'autre. */
    CORRECTION_VENTE,

    /** Entree ou sortie saisie a la main, sans document qui la porte. */
    SAISIE_MANUELLE,

    /**
     * Rattrapage d'un ecart constate au comptage.
     *
     * C'est le seul motif qui ne vient d'aucun document ni d'aucun geste de comptoir : il dit que
     * l'etagere ne disait pas la meme chose que le logiciel, et lequel des deux a eu raison.
     */
    INVENTAIRE
}
