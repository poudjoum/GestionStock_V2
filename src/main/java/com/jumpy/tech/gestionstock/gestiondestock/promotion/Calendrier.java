package com.jumpy.tech.gestionstock.gestiondestock.promotion;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Le jour qu'il est au magasin.
 *
 * Une campagne court « du 1er au 15 » a l'heure du magasin, et non a celle du serveur, qui tourne
 * en UTC : a Douala, un achat fait a 0 h 30 le 16 serait sinon encore compte le 15.
 */
@Component
public class Calendrier {

    private final ZoneId fuseau;
    private final Clock horloge;

    @Autowired
    public Calendrier(@Value("${app.fuseauHoraire:Africa/Douala}") String fuseauHoraire) {
        this(ZoneId.of(fuseauHoraire), Clock.systemUTC());
    }

    Calendrier(ZoneId fuseau, Clock horloge) {
        this.fuseau = fuseau;
        this.horloge = horloge;
    }

    /** Le fuseau du magasin : c'est lui qui decoupe les journees des rapports. */
    public ZoneId fuseau() {
        return fuseau;
    }

    public LocalDate aujourdhui() {
        return LocalDate.now(horloge.withZone(fuseau));
    }

    public LocalDate jourDe(Instant instant) {
        return instant == null ? aujourdhui() : instant.atZone(fuseau).toLocalDate();
    }

    /** Pour les tests : un calendrier arrete a un instant donne. */
    public static Calendrier fixe(Instant maintenant, ZoneId fuseau) {
        return new Calendrier(fuseau, Clock.fixed(maintenant, fuseau));
    }
}
