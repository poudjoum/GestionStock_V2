package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Le jeton qui permet d'en obtenir un autre.
 *
 * Un JWT ne se revoque pas : une fois signe, il vaut jusqu'a son expiration, et fermer un compte
 * ne le rappelle pas. C'etait tout le probleme d'un jeton d'acces de 24 h — un employe renvoye
 * gardait ses acces jusqu'au lendemain.
 *
 * Celui-ci vit en base, donc se revoque. Il ne porte aucun droit par lui-meme : il ne sert qu'a
 * redemander un jeton d'acces, et chaque echange le remplace par un nouveau.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "jeton_rafraichissement")
public class JetonRafraichissement extends AbstractEntity {

    @Column(name = "jeton", nullable = false, unique = true, length = 64)
    private String jeton;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_utilisateur")
    private Utilisateur utilisateur;

    @Column(name = "expiration", nullable = false)
    private Instant expiration;

    /**
     * Quand il a cesse de valoir, et pourquoi il n'est pas simplement supprime.
     *
     * Un jeton revoque qu'on represente est le signe qu'il a ete copie : le supprimer effacerait
     * la seule trace permettant de s'en apercevoir.
     */
    @Column(name = "revoque_le")
    private Instant revoqueLe;

    /** Pourquoi, parce que cela decide de ce qui se passe si ce jeton revient. */
    @Enumerated(EnumType.STRING)
    @Column(name = "motif_revocation", length = 20)
    private MotifRevocation motifRevocation;

    public boolean estValide(Instant maintenant) {
        return revoqueLe == null && expiration.isAfter(maintenant);
    }

    public void revoquer(Instant quand, MotifRevocation motif) {
        this.revoqueLe = quand;
        this.motifRevocation = motif;
    }
}
