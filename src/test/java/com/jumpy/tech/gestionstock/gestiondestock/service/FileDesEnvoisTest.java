package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.entities.CanalEnvoi;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Envoi;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatEnvoi;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EnvoiRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.Impl.Expediteur;
import com.jumpy.tech.gestionstock.gestiondestock.service.Impl.LivraisonUnitaire;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La file des envois : ce qui doit quitter l'application, et ce qui arrive quand la livraison rate.
 *
 * Toute la raison d'etre de cette file est qu'un serveur SMTP tombe ne fasse pas echouer une
 * vente. L'operation metier pose une ligne — ce qui ne peut pas rater — et la livraison vient
 * apres, ailleurs, avec le droit d'echouer.
 */
class FileDesEnvoisTest extends AbstractIntegrationTest {

    @Autowired
    private EnvoiRepository envoiRepository;
    @Autowired
    private LivraisonUnitaire livraison;
    @Autowired
    private Expediteur expediteur;
    @Autowired
    private NotificationService notifications;

    private Envoi unEnvoiEnAttente() {
        notifications.mettreEnFile(UUID.randomUUID() + "@exemple.test",
                "Sujet " + UUID.randomUUID(), "Corps", null);
        return envoiRepository.findAll().stream()
                .filter(e -> e.getEtat() == EtatEnvoi.A_ENVOYER)
                .reduce((premier, dernier) -> dernier)
                .orElseThrow();
    }

    @Test
    void un_envoi_naît_en_attente_et_pret_a_partir() {
        Envoi envoi = unEnvoiEnAttente();

        assertThat(envoi.getEtat()).isEqualTo(EtatEnvoi.A_ENVOYER);
        assertThat(envoi.getCanal()).isEqualTo(CanalEnvoi.EMAIL);
        assertThat(envoi.getTentatives()).isZero();
        assertThat(envoi.getProchaineTentative()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void un_destinataire_sans_adresse_ne_met_rien_en_file() {
        long avant = envoiRepository.count();

        notifications.mettreEnFile(null, "Sujet", "Corps", null);
        notifications.mettreEnFile("   ", "Sujet", "Corps", null);

        // Un client sans adresse ne recoit rien, et ce n'est pas une erreur.
        assertThat(envoiRepository.count()).isEqualTo(avant);
    }

    @Test
    void sans_smtp_rien_ne_part_et_rien_n_est_perdu() {
        Envoi envoi = unEnvoiEnAttente();

        // `spring.mail.host` est vide dans les tests, comme sur une installation ou MAIL_HOST
        // n'est pas encore renseigne.
        int partis = expediteur.traiterLaFile();

        assertThat(partis).isZero();
        // Ne pas abandonner est volontaire : une file qui se vide dans le neant serait pire
        // qu'une file qui attend.
        assertThat(envoiRepository.findById(envoi.getId())).get()
                .extracting(Envoi::getEtat).isEqualTo(EtatEnvoi.A_ENVOYER);
    }

    @Test
    void une_livraison_reussie_marque_l_envoi_parti() {
        Envoi envoi = unEnvoiEnAttente();

        boolean parti = livraison.livrer(envoi.getId(), e -> { /* le serveur a accepte */ });

        assertThat(parti).isTrue();
        Envoi relu = envoiRepository.findById(envoi.getId()).orElseThrow();
        assertThat(relu.getEtat()).isEqualTo(EtatEnvoi.ENVOYE);
        assertThat(relu.getEnvoyeLe()).isNotNull();
    }

    @Test
    void un_envoi_deja_parti_ne_repart_pas() {
        Envoi envoi = unEnvoiEnAttente();
        livraison.livrer(envoi.getId(), e -> { });

        boolean reparti = livraison.livrer(envoi.getId(), e -> { });

        // Sans cette garde, un passage concurrent enverrait sa facture deux fois au client.
        assertThat(reparti).isFalse();
    }

    @Test
    void un_echec_est_note_et_reprogramme_pas_perdu() {
        Envoi envoi = unEnvoiEnAttente();

        boolean parti = livraison.livrer(envoi.getId(), e -> {
            throw new IllegalStateException("serveur injoignable");
        });

        assertThat(parti).isFalse();
        Envoi relu = envoiRepository.findById(envoi.getId()).orElseThrow();
        assertThat(relu.getEtat()).isEqualTo(EtatEnvoi.A_ENVOYER);
        assertThat(relu.getTentatives()).isEqualTo(1);
        assertThat(relu.getDerniereErreur()).contains("injoignable");
        // L'attente doit repousser la reprise : retenter dans la seconde ne ferait que du bruit.
        assertThat(relu.getProchaineTentative()).isAfter(Instant.now());
    }

    @Test
    void l_attente_double_a_chaque_echec() {
        Envoi envoi = unEnvoiEnAttente();

        livraison.livrer(envoi.getId(), e -> { throw new IllegalStateException("non"); });
        Instant apresUn = envoiRepository.findById(envoi.getId()).orElseThrow().getProchaineTentative();

        // La reprise est repoussee ; on la ramene pour ne pas attendre une minute en test.
        avancerLHorloge(envoi.getId());
        livraison.livrer(envoi.getId(), e -> { throw new IllegalStateException("non"); });
        Instant apresDeux = envoiRepository.findById(envoi.getId()).orElseThrow().getProchaineTentative();

        assertThat(apresDeux).isAfter(apresUn);
    }

    @Test
    void apres_six_echecs_l_envoi_est_abandonne_mais_reste_lisible() {
        Envoi envoi = unEnvoiEnAttente();

        for (int i = 0; i < Envoi.TENTATIVES_MAX; i++) {
            avancerLHorloge(envoi.getId());
            livraison.livrer(envoi.getId(), e -> {
                throw new IllegalStateException("adresse inexistante");
            });
        }

        Envoi relu = envoiRepository.findById(envoi.getId()).orElseThrow();
        assertThat(relu.getEtat()).isEqualTo(EtatEnvoi.ABANDONNE);
        assertThat(relu.getTentatives()).isEqualTo(Envoi.TENTATIVES_MAX);
        // Abandonne n'est pas efface : une facture jamais partie est une question qu'on se
        // posera, et supprimer la ligne supprimerait la reponse.
        assertThat(relu.getDerniereErreur()).contains("adresse inexistante");
    }

    @Test
    void un_echec_n_emporte_pas_les_reussites_du_meme_paquet() {
        Envoi quiRate = unEnvoiEnAttente();
        Envoi quiPasse = unEnvoiEnAttente();

        livraison.livrer(quiRate.getId(), e -> { throw new IllegalStateException("non"); });
        livraison.livrer(quiPasse.getId(), e -> { });

        // Chaque envoi dans sa propre transaction : sans cela, le premier echec emporterait le
        // compte-rendu des autres, qui repartiraient au passage suivant — et le client recevrait
        // sa facture plusieurs fois.
        assertThat(envoiRepository.findById(quiPasse.getId())).get()
                .extracting(Envoi::getEtat).isEqualTo(EtatEnvoi.ENVOYE);
        assertThat(envoiRepository.findById(quiRate.getId())).get()
                .extracting(Envoi::getEtat).isEqualTo(EtatEnvoi.A_ENVOYER);
    }

    @Test
    void une_erreur_trop_longue_est_tronquee_a_la_taille_de_la_colonne() {
        Envoi envoi = unEnvoiEnAttente();
        String trace = "x".repeat(2000);

        livraison.livrer(envoi.getId(), e -> { throw new IllegalStateException(trace); });

        // La colonne fait 500 caracteres ; une trace d'erreur en fait volontiers davantage, et
        // l'ecriture echouerait sans troncature — l'echec d'un envoi deviendrait une exception.
        assertThat(envoiRepository.findById(envoi.getId()).orElseThrow().getDerniereErreur())
                .hasSizeLessThanOrEqualTo(500)
                .endsWith("...");
    }

    /** Ramene la prochaine tentative dans le passe, pour ne pas attendre le delai en test. */
    private void avancerLHorloge(Long idEnvoi) {
        Envoi envoi = envoiRepository.findById(idEnvoi).orElseThrow();
        envoi.setProchaineTentative(Instant.now().minusSeconds(1));
        envoiRepository.save(envoi);
    }

    @Test
    void l_expediteur_ne_traite_que_ce_dont_l_heure_est_venue() {
        // Verifie la requete de selection plutot que le comportement d'envoi : sans SMTP, rien ne
        // part, mais le choix de ce qui serait traite doit deja etre juste.
        Envoi envoi = unEnvoiEnAttente();
        envoi.setProchaineTentative(Instant.now().plusSeconds(3600));
        envoiRepository.save(envoi);

        AtomicInteger vus = new AtomicInteger();
        envoiRepository.findAllByCanalAndEtatAndProchaineTentativeLessThanEqualOrderByIdAsc(
                        CanalEnvoi.EMAIL, EtatEnvoi.A_ENVOYER, Instant.now(),
                        org.springframework.data.domain.PageRequest.of(0, 50))
                .forEach(e -> { if (e.getId().equals(envoi.getId())) vus.incrementAndGet(); });

        assertThat(vus.get()).isZero();
    }
}
