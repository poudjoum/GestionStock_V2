package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Un appareil qui veut etre prevenu, meme application fermee.
 *
 * Un appareil, et non un compte : le meme gerant peut l'etre sur son telephone et sur la caisse.
 * L'abonnement appartient au dernier compte qui s'y est connecte — une caisse partagee ne doit pas
 * continuer de recevoir les alertes de celui qui est parti.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "abonnement_push")
public class AbonnementPush extends AbstractEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_utilisateur")
    private Utilisateur utilisateur;

    @Column(name = "id_entreprise")
    private Long idEntreprise;

    /** L'adresse du service push, qui identifie l'appareil. */
    @Column(name = "adresse", nullable = false, length = 1000)
    private String adresse;

    /** La cle publique du navigateur, 65 octets en base64url. */
    @Column(name = "cle_p256dh", nullable = false, length = 120)
    private String cleP256dh;

    /** Le secret d'authentification du navigateur, 16 octets en base64url. */
    @Column(name = "cle_auth", nullable = false, length = 40)
    private String cleAuth;

    @Column(name = "appareil", length = 200)
    private String appareil;
}
