package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumpy.tech.gestionstock.gestiondestock.entities.AbonnementPush;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Envoi;
import com.jumpy.tech.gestionstock.gestiondestock.repository.AbonnementPushRepository;
import com.jumpy.tech.gestionstock.gestiondestock.webpush.ChiffrementWebPush;
import com.jumpy.tech.gestionstock.gestiondestock.webpush.PortePush;
import com.jumpy.tech.gestionstock.gestiondestock.webpush.Vapid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Web Push : ce qui fait arriver une notification sur un appareil, application fermee.
 *
 * Le chemin d'un message : une notification est ecrite dans l'application ; pour chaque appareil
 * abonne de son destinataire, un envoi `PUSH` entre dans la file, comme un courriel ; l'expediteur
 * le chiffre pour cet appareil, le signe, et le depose chez le service push du navigateur — celui
 * de Google, de Mozilla, d'Apple ou de Microsoft —, qui le remet a l'appareil.
 *
 * <b>Sans cles VAPID, rien.</b> Le front ne propose pas de s'abonner, et aucun envoi n'est mis en
 * file. C'est l'equivalent de `EMAIL_HOST` vide, a une difference pres : un courriel en attente
 * garde son sens des semaines plus tard, une alerte de stock non.
 */
@Component
@Slf4j
public class ServicePush {

    /**
     * Les services push des navigateurs, et eux seuls.
     *
     * L'adresse d'un abonnement vient du navigateur, donc de l'appelant : sans cette liste, il
     * suffirait de s'abonner avec `http://192.168.1.1/…` pour que ce serveur aille frapper, a
     * chaque alerte, a une porte de son propre reseau.
     */
    private static final List<String> SERVICES_ADMIS = List.of(
            "fcm.googleapis.com",            // Chrome, Edge sur Android, Opera, Brave, Samsung
            "push.services.mozilla.com",     // Firefox
            "notify.windows.com",            // Edge sur Windows
            "push.apple.com");               // Safari

    /** Une alerte de stock vieille d'un jour ne sert plus : au-dela, le service push la jette. */
    private static final int DUREE_DE_VIE_SECONDES = 24 * 3600;

    private final Optional<Vapid> vapid;
    private final PortePush porte;
    private final AbonnementPushRepository abonnements;
    private final ObjectMapper json;

    public ServicePush(@Value("${app.push.clePublique:}") String clePublique,
                       @Value("${app.push.clePrivee:}") String clePrivee,
                       @Value("${app.push.sujet:}") String sujet,
                       PortePush porte,
                       AbonnementPushRepository abonnements,
                       ObjectMapper json) {
        this.porte = porte;
        this.abonnements = abonnements;
        this.json = json;
        if (StringUtils.hasText(clePublique) && StringUtils.hasText(clePrivee)) {
            // Une cle mal recopiee fait echouer le demarrage, avec son message : decouverte au
            // premier envoi, elle passerait pour une panne du service push.
            this.vapid = Optional.of(new Vapid(clePublique, clePrivee,
                    StringUtils.hasText(sujet) ? sujet : "mailto:contact@exemple.invalid"));
            if (!StringUtils.hasText(sujet)) {
                log.warn("VAPID_SUJET est vide : les services push n'ont aucun moyen de joindre "
                        + "l'exploitant avant de bloquer ce serveur. Renseignez une adresse mailto:.");
            }
        } else {
            this.vapid = Optional.empty();
        }
    }

    public boolean configure() {
        return vapid.isPresent();
    }

    /** La cle que le front donne au navigateur pour s'abonner. */
    public Optional<String> clePublique() {
        return vapid.map(Vapid::clePublique);
    }

    /** Vrai pour l'adresse d'un service push de navigateur, en HTTPS. */
    public static boolean adresseAdmise(String adresse) {
        try {
            URI uri = URI.create(adresse);
            String hote = uri.getHost();
            return "https".equals(uri.getScheme()) && hote != null && uri.getPort() == -1
                    && SERVICES_ADMIS.stream().anyMatch(s -> hote.equals(s) || hote.endsWith("." + s));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Le message tel que le service worker d'Angular l'attend.
     *
     * Le clic sur la notification ouvre l'application a l'endroit de l'alerte — ou y ramene
     * l'onglet deja ouvert. L'etiquette fait qu'une alerte qui revient remplace la precedente sur
     * l'appareil au lieu de s'y empiler.
     */
    public String message(String titre, String corps, String lien, String etiquette) {
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("title", titre);
        if (StringUtils.hasText(corps)) {
            notification.put("body", corps);
        }
        notification.put("icon", "/icons/icon-192x192.png");
        notification.put("badge", "/icons/icon-72x72.png");
        if (StringUtils.hasText(etiquette)) {
            notification.put("tag", etiquette);
        }
        notification.put("data", Map.of("onActionClick", Map.of("default", Map.of(
                "operation", "navigateLastFocusedOrOpen",
                "url", StringUtils.hasText(lien) ? lien : "/notifications"))));
        try {
            return json.writeValueAsString(Map.of("notification", notification));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Message push impossible a ecrire", e);
        }
    }

    /**
     * Livre un envoi `PUSH` : appele par la file, dans la transaction de cet envoi.
     *
     * Ce que rend le service push decide de la suite. 201 : parti. 404 ou 410 : l'appareil s'est
     * desabonne, ou l'abonnement a expire — il est oublie, et l'envoi abandonne sans reessayer.
     * 429 et 5xx : le service est surcharge ou en panne, la file retentera.
     */
    public void livrer(Envoi envoi) {
        Vapid identite = vapid.orElseThrow(() -> new EnvoiImpossible("Web Push n'est pas configuré"));
        AbonnementPush abonnement = abonnements.findById(Long.valueOf(envoi.getDestination()))
                .orElseThrow(() -> new EnvoiImpossible("L'appareil s'est désabonné"));

        URI adresse = URI.create(abonnement.getAdresse());
        byte[] corps = ChiffrementWebPush.chiffrer(
                envoi.getCorps().getBytes(StandardCharsets.UTF_8),
                Base64.getUrlDecoder().decode(abonnement.getCleP256dh()),
                Base64.getUrlDecoder().decode(abonnement.getCleAuth()));

        Map<String, String> entetes = new LinkedHashMap<>();
        entetes.put("Authorization", identite.entete(adresse, Instant.now()));
        entetes.put("Content-Encoding", "aes128gcm");
        entetes.put("Content-Type", "application/octet-stream");
        entetes.put("TTL", String.valueOf(DUREE_DE_VIE_SECONDES));
        entetes.put("Urgency", "normal");

        int statut = porte.deposer(adresse, entetes, corps);
        if (statut >= 200 && statut < 300) {
            return;
        }
        if (statut == 404 || statut == 410) {
            abonnements.delete(abonnement);
            throw new EnvoiImpossible("Abonnement expiré (" + statut + ") : l'appareil est oublié");
        }
        if (statut == 429 || statut >= 500) {
            throw new IllegalStateException("Service push indisponible (" + statut + ")");
        }
        // 400, 403, 413 : le service a lu l'envoi et le refuse. Le reessayer tel quel ne changera
        // rien. Un 403 dit le plus souvent que les cles VAPID ne sont plus celles de l'abonnement ;
        // l'abonnement n'est pas efface pour autant, au cas ou ce serait la configuration d'ici
        // qui soit fausse.
        throw new EnvoiImpossible("Refusé par le service push (" + statut + ")");
    }
}
