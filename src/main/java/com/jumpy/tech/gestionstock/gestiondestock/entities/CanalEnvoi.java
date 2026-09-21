package com.jumpy.tech.gestionstock.gestiondestock.entities;

/**
 * Par ou un message quitte l'application.
 *
 * Un seul canal pour l'instant. `PUSH` viendra avec le front : notifier le navigateur d'une
 * application qui n'existe pas encore ne mene nulle part, et cela demande des cles VAPID, une
 * table d'abonnements et un service worker cote client.
 */
public enum CanalEnvoi {

    EMAIL
}
