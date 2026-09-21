package com.jumpy.tech.gestionstock.gestiondestock.entities;

/**
 * Le cycle de vie d'une commande, client ou fournisseur.
 *
 * Il manquait entierement : une commande enregistree etait definitive, et c'est ce qui obligeait
 * a faire entrer la marchandise d'une commande fournisseur des son enregistrement, faute de
 * moment ou constater sa reception.
 *
 * Deux etats sont terminaux. Une commande livree ne se deprogramme pas — la marchandise a bouge,
 * et l'annuler laisserait le stock mentir ; le retour se constate par une autre operation. Une
 * commande annulee ne se reprend pas non plus : on en saisit une nouvelle, ce qui laisse une
 * trace de ce qui s'est passe.
 */
public enum EtatCommande {

    EN_PREPARATION,
    VALIDEE,
    /**
     * Une partie de la marchandise est arrivee, le reste est attendu.
     *
     * Cet etat ne s'annule pas : du stock est deja entre, et l'annulation laisserait des
     * quantites sans commande pour les expliquer. Ce qui reste attendu se lit ligne par ligne.
     */
    PARTIELLEMENT_LIVREE,
    LIVREE,
    ANNULEE;

    public boolean estTerminal() {
        return this == LIVREE || this == ANNULEE;
    }

    /** Une commande dont une partie est deja arrivee est engagee : ses lignes ne bougent plus. */
    public boolean estEngagee() {
        return estTerminal() || this == PARTIELLEMENT_LIVREE;
    }

    /**
     * Les transitions permises. Elles vivent ici, avec les etats eux-memes, plutot que dans
     * chacun des deux services : recopiees, elles auraient diverge au premier ajout d'etat.
     */
    public boolean peutPasserA(EtatCommande cible) {
        if (cible == null || cible == this) {
            return false;
        }
        return switch (this) {
            case EN_PREPARATION -> cible == VALIDEE || cible == ANNULEE;
            case VALIDEE -> cible == LIVREE || cible == PARTIELLEMENT_LIVREE || cible == ANNULEE;
            // Le reliquat arrive, ou il n'arrive pas : dans les deux cas, ce qui est deja entre
            // interdit de revenir en arriere.
            case PARTIELLEMENT_LIVREE -> cible == LIVREE;
            case LIVREE, ANNULEE -> false;
        };
    }
}
