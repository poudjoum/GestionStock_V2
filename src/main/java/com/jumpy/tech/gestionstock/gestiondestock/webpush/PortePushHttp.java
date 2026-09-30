package com.jumpy.tech.gestionstock.gestiondestock.webpush;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Le depot reel, par le client HTTP du JDK. */
@Component
public class PortePushHttp implements PortePush {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            // Jamais de redirection suivie : l'adresse a ete verifiee a l'abonnement, et un
            // service push n'a aucune raison de renvoyer ailleurs.
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public int deposer(URI adresse, Map<String, String> entetes, byte[] corps) {
        HttpRequest.Builder requete = HttpRequest.newBuilder(adresse)
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofByteArray(corps));
        entetes.forEach(requete::header);
        try {
            return http.send(requete.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException e) {
            throw new UncheckedIOException("Service push injoignable : " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Envoi push interrompu", e);
        }
    }
}
