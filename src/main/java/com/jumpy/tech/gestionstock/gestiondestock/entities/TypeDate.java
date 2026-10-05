package com.jumpy.tech.gestionstock.gestiondestock.entities;

/** La date que porte un lot, et ce qu'elle interdit une fois passee. */
public enum TypeDate {
    /** « A consommer jusqu'au » : depassee, le lot ne se vend plus. */
    DLC,
    /** « De preference avant » : depassee, il se vend encore, avec un avertissement. */
    DLUO
}
