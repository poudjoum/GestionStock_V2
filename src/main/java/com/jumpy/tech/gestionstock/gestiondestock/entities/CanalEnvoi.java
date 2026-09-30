package com.jumpy.tech.gestionstock.gestiondestock.entities;

/**
 * Par ou un message quitte l'application.
 *
 * `EMAIL` : la destination est une adresse de courriel. `PUSH` : la destination est l'identifiant
 * d'un `AbonnementPush`, et le corps le message a chiffrer pour cet appareil au moment de partir.
 */
public enum CanalEnvoi {

    EMAIL,
    PUSH
}
