package com.jumpy.tech.gestionstock.gestiondestock.entities;

/**
 * Par quel moyen l'argent est entre.
 *
 * Ce n'est pas une curiosite comptable : c'est ce qui dit ou aller verifier qu'il est bien
 * arrive. Les especes se comptent en caisse, un paiement mobile money se retrouve par son numero
 * de transaction, un cheque peut revenir impaye.
 */
public enum ModeReglement {

    ESPECES,
    MOBILE_MONEY,
    VIREMENT,
    CHEQUE,
    AUTRE
}
