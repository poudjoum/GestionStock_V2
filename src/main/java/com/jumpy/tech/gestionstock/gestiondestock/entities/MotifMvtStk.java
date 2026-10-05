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
    INVENTAIRE,

    // Les motifs d'une saisie a la main. Tous aboutissaient a SAISIE_MANUELLE, si bien que la
    // demarque — ce que le magasin perd sans le vendre — ne se mesurait pas : une casse, une
    // peremption et un echantillon offert se confondaient.

    /** Marchandise disparue : vol, erreur de livraison constatee apres coup. */
    PERTE,

    /** Marchandise abimee, invendable. */
    CASSE,

    /** Date limite depassee : retiree de la vente. */
    PEREMPTION,

    /** Marchandise rendue au fournisseur, hors commande. */
    RETOUR_FOURNISSEUR,

    /** Marchandise rapportee par un client, hors annulation de vente. */
    RETOUR_CLIENT,

    /** Utilisee par l'entreprise elle-meme : echantillon, usage interne. */
    CONSOMMATION_INTERNE;

    /** Un motif qu'une saisie a la main peut declarer pour une entree. */
    public boolean saisissableEnEntree() {
        return this == SAISIE_MANUELLE || this == RETOUR_CLIENT;
    }

    /** Un motif qu'une saisie a la main peut declarer pour une sortie. */
    public boolean saisissableEnSortie() {
        return this == SAISIE_MANUELLE || this == PERTE || this == CASSE || this == PEREMPTION
                || this == RETOUR_FOURNISSEUR || this == CONSOMMATION_INTERNE;
    }
}
