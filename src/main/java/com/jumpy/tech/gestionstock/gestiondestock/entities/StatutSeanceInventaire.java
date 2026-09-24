package com.jumpy.tech.gestionstock.gestiondestock.entities;

/**
 * Ou en est une seance d'inventaire.
 *
 * Trois etats et pas un de plus : on compte, puis ou bien l'on valide — et le stock est corrige —
 * ou bien l'on renonce. Une seance abandonnee reste lisible : savoir qu'un comptage a ete
 * commence puis laisse en plan fait partie de l'histoire du magasin.
 */
public enum StatutSeanceInventaire {
    OUVERTE,
    VALIDEE,
    ABANDONNEE
}
