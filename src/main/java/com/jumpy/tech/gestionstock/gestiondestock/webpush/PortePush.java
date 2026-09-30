package com.jumpy.tech.gestionstock.gestiondestock.webpush;

import java.net.URI;
import java.util.Map;

/**
 * Le depot d'un message chez un service push : un POST, et le statut qu'il rend.
 *
 * A part du reste pour que les tests le remplacent : ils verifient ce que l'application fait d'un
 * 201, d'un 410 ou d'un 503 sans dependre des serveurs de Google.
 */
public interface PortePush {

    /** Rend le statut HTTP. Une erreur de reseau leve une exception : elle sera retentee. */
    int deposer(URI adresse, Map<String, String> entetes, byte[] corps);
}
