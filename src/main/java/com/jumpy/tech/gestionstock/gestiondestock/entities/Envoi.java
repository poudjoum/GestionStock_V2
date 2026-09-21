package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * Un message qui doit quitter l'application.
 *
 * Il est ecrit dans la transaction de l'operation qui le declenche, et livre plus tard, par un
 * autre chemin. C'est ce qui garantit qu'un serveur SMTP tombe ne fait pas echouer une vente :
 * l'application n'a qu'a poser une ligne, ce qui ne peut pas rater.
 *
 * Le destinataire est une adresse, pas un compte : le client qui recoit sa facture n'en a pas.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "envoi")
public class Envoi extends AbstractEntity {

    /** Combien de fois on reessaie avant de renoncer. */
    public static final int TENTATIVES_MAX = 6;

    /** L'attente avant la premiere reprise ; elle double ensuite a chaque echec. */
    private static final Duration ATTENTE_INITIALE = Duration.ofMinutes(1);

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, length = 20)
    private CanalEnvoi canal;

    @Column(name = "destination", nullable = false, length = 320)
    private String destination;

    @Column(name = "sujet", nullable = false, length = 300)
    private String sujet;

    @Column(name = "corps", nullable = false, columnDefinition = "text")
    private String corps;

    @Enumerated(EnumType.STRING)
    @Column(name = "etat", nullable = false, length = 20)
    private EtatEnvoi etat;

    @Column(name = "tentatives", nullable = false)
    private int tentatives;

    @Column(name = "prochaine_tentative", nullable = false)
    private Instant prochaineTentative;

    @Column(name = "derniere_erreur", length = 500)
    private String derniereErreur;

    @Column(name = "envoye_le")
    private Instant envoyeLe;

    @Column(name = "id_entreprise")
    private Long idEntreprise;

    public void reussi(Instant quand) {
        this.etat = EtatEnvoi.ENVOYE;
        this.envoyeLe = quand;
        this.derniereErreur = null;
    }

    /**
     * Note l'echec et repousse la reprise, en doublant l'attente a chaque fois.
     *
     * Une adresse momentanement injoignable retentee toutes les minutes remplit les journaux sans
     * rien arranger ; au bout de six essais, on renonce et la ligne reste lisible avec sa cause.
     */
    public void echoue(Instant quand, String erreur) {
        this.tentatives++;
        this.derniereErreur = tronque(erreur);
        if (tentatives >= TENTATIVES_MAX) {
            this.etat = EtatEnvoi.ABANDONNE;
            return;
        }
        this.prochaineTentative = quand.plus(ATTENTE_INITIALE.multipliedBy(1L << (tentatives - 1)));
    }

    /** La colonne fait 500 caracteres ; une trace d'erreur en fait volontiers davantage. */
    private static String tronque(String erreur) {
        if (erreur == null) {
            return null;
        }
        return erreur.length() <= 500 ? erreur : erreur.substring(0, 497) + "...";
    }
}
