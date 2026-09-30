package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

/**
 * Un envoi qui ne partira jamais, quel que soit le nombre d'essais.
 *
 * La file retente ce qui echoue, en doublant l'attente : c'est juste pour un serveur
 * momentanement injoignable, et inutile pour un appareil qui s'est desabonne. Lever celle-ci
 * abandonne l'envoi au premier essai.
 */
public class EnvoiImpossible extends RuntimeException {

    public EnvoiImpossible(String raison) {
        super(raison);
    }
}
