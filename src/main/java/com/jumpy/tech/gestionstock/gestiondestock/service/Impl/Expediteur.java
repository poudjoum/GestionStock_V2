package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.entities.CanalEnvoi;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Envoi;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatEnvoi;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EnvoiRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

/**
 * Ce qui vide la file : la livraison, separee de l'enregistrement.
 *
 * C'est toute la raison d'etre de la table `envoi`. L'operation metier pose une ligne — ce qui ne
 * peut pas rater — et cette classe, plus tard et ailleurs, essaie de la livrer. Une vente ne se
 * refuse donc pas parce que le serveur SMTP est tombe.
 *
 * Tant que SMTP n'est pas configure, rien ne part et rien n'echoue : les envois restent en
 * attente, et partiront tels quels le jour ou les variables arrivent. Ne pas les abandonner est
 * volontaire — une file qui se vide dans le neant serait pire qu'une file qui attend.
 */
@Component
@Slf4j
public class Expediteur {

    /** Combien d'envois par passage. Une file grossie pendant une panne se vide par paquets. */
    private static final int TAILLE_DU_PAQUET = 50;

    private final EnvoiRepository envoiRepository;
    private final LivraisonUnitaire livraison;
    private final JavaMailSender mailSender;
    private final String hoteSmtp;
    private final String expediteur;

    /** Pour ne repeter qu'une fois que SMTP n'est pas configure, au lieu d'a chaque passage. */
    private boolean absenceDeSmtpDejaSignalee;

    public Expediteur(EnvoiRepository envoiRepository,
                      LivraisonUnitaire livraison,
                      JavaMailSender mailSender,
                      @Value("${spring.mail.host:}") String hoteSmtp,
                      @Value("${app.notifications.expediteur:}") String expediteur) {
        this.envoiRepository = envoiRepository;
        this.livraison = livraison;
        this.mailSender = mailSender;
        this.hoteSmtp = hoteSmtp;
        this.expediteur = expediteur;
    }

    /**
     * Un passage toutes les minutes par defaut.
     *
     * L'intervalle se regle, et se met a zero pour arreter la boucle — ce que font les tests, qui
     * appellent `traiterLaFile` eux-memes pour ne pas dependre d'une horloge.
     */
    @Scheduled(fixedDelayString = "${app.notifications.intervalleMs:60000}",
            initialDelayString = "${app.notifications.intervalleMs:60000}")
    public void passagePeriodique() {
        traiterLaFile();
    }

    /** Rend le nombre d'envois effectivement partis. */
    public int traiterLaFile() {
        if (!smtpConfigure()) {
            if (!absenceDeSmtpDejaSignalee) {
                long enAttente = envoiRepository.countByEtat(EtatEnvoi.A_ENVOYER);
                log.warn("SMTP non configure : {} courriel(s) en attente. Renseignez EMAIL_HOST "
                        + "pour qu'ils partent — ils sont conserves jusque-la.", enAttente);
                absenceDeSmtpDejaSignalee = true;
            }
            return 0;
        }
        absenceDeSmtpDejaSignalee = false;

        List<Envoi> aTraiter = envoiRepository
                .findAllByCanalAndEtatAndProchaineTentativeLessThanEqualOrderByIdAsc(
                        CanalEnvoi.EMAIL, EtatEnvoi.A_ENVOYER, Instant.now(),
                        PageRequest.of(0, TAILLE_DU_PAQUET));

        int partis = 0;
        for (Envoi envoi : aTraiter) {
            // Chaque envoi dans sa propre transaction : sans cela, le premier echec emporterait
            // avec lui le compte-rendu de tous les autres.
            if (livraison.livrer(envoi.getId(), this::envoyer)) {
                partis++;
            }
        }
        if (!aTraiter.isEmpty()) {
            log.info("File des envois : {} traite(s), {} parti(s)", aTraiter.size(), partis);
        }
        return partis;
    }

    private void envoyer(Envoi envoi) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (StringUtils.hasText(expediteur)) {
            message.setFrom(expediteur);
        }
        message.setTo(envoi.getDestination());
        message.setSubject(envoi.getSujet());
        message.setText(envoi.getCorps());
        mailSender.send(message);
    }

    /**
     * `spring.mail.host` vaut la chaine vide tant que EMAIL_HOST n'est pas renseigne. Spring
     * construit bien un expediteur, mais il pointe vers nulle part : mieux vaut ne pas l'appeler
     * que collectionner des echecs qui n'apprennent rien.
     */
    private boolean smtpConfigure() {
        return StringUtils.hasText(hoteSmtp);
    }
}
