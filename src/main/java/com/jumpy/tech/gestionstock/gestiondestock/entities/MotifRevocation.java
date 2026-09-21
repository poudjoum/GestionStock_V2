package com.jumpy.tech.gestionstock.gestiondestock.entities;

/**
 * Pourquoi un jeton de rafraichissement a cesse de valoir.
 *
 * La distinction n'est pas decorative : elle decide de ce qui se passe quand ce jeton revient.
 * Sans elle, se deconnecter de son telephone puis voir l'application rejouer une derniere fois
 * sa requete fermait aussi la caisse restee ouverte au comptoir — sans le moindre voleur.
 */
public enum MotifRevocation {

    /** Le titulaire s'est deconnecte de cet appareil. Le rejouer est une maladresse, pas un vol. */
    DECONNEXION,

    /**
     * Remplace par le jeton suivant lors d'un echange.
     *
     * Celui-la ne doit jamais revenir : le client qui l'a echange en a recu un autre. S'il revient,
     * c'est qu'une copie circule — et tout le compte se ferme.
     */
    ROTATION,

    /** Ferme d'autorite : acces retire, mot de passe change, ou copie detectee. */
    SECURITE
}
