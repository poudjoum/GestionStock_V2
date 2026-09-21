package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Ce qu'un utilisateur lit dans l'application.
 *
 * Adressee a une personne, et non a un role : l'etat « lu » est personnel, et une alerte que le
 * magasinier a traitee ne doit pas disparaitre de l'ecran du gerant. Une alerte qui concerne
 * plusieurs personnes s'ecrit donc en autant de lignes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "notification")
public class Notification extends AbstractEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_destinataire")
    private Utilisateur destinataire;

    @Column(name = "id_entreprise")
    private Long idEntreprise;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private TypeNotification type;

    @Column(name = "titre", nullable = false, length = 200)
    private String titre;

    @Column(name = "corps", columnDefinition = "text")
    private String corps;

    /** Ou aller dans l'application. Un chemin, jamais une URL : le serveur ignore le domaine. */
    @Column(name = "lien", length = 300)
    private String lien;

    /**
     * De quoi eviter de repeter la meme alerte.
     *
     * Un article sous son seuil le reste a chaque vente : sans cette cle, le magasinier recevrait
     * une notification par article vendu. Tant qu'une notification portant la meme cle n'est pas
     * lue, on n'en ecrit pas d'autre.
     */
    @Column(name = "cle", length = 120)
    private String cle;

    @Column(name = "lu_le")
    private Instant luLe;

    public boolean estLue() {
        return luLe != null;
    }
}
