package com.jumpy.tech.gestionstock.gestiondestock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Un abonnement tel que le navigateur le donne : `PushSubscription.toJSON()`, sans rien changer.
 *
 * Reprendre sa forme exacte evite au front toute traduction — et toute occasion de se tromper de
 * cle entre `p256dh` et `auth`.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbonnementPushDto {

    /** L'adresse du service push ou deposer les messages pour cet appareil. */
    private String endpoint;

    private Cles keys;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cles {
        /** La cle publique du navigateur, 65 octets en base64url. */
        private String p256dh;
        /** Son secret d'authentification, 16 octets en base64url. */
        private String auth;
    }
}
