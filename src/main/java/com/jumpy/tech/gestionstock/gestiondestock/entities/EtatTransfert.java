package com.jumpy.tech.gestionstock.gestiondestock.entities;

/** Ou en est un transfert. */
public enum EtatTransfert {
    /** En preparation : il se corrige, rien n'a bouge. */
    BROUILLON,
    /** Parti : la marchandise a quitte le site de depart, elle est en transit. */
    EXPEDIE,
    /** Arrive : le site d'arrivee a compte ce qui est descendu du camion. */
    RECU,
    /** Abandonne avant le depart. */
    ANNULE
}
