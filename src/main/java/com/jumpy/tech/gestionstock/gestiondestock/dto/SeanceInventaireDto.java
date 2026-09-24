package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.SeanceInventaire;
import com.jumpy.tech.gestionstock.gestiondestock.entities.StatutSeanceInventaire;

import java.time.Instant;

/**
 * Une seance d'inventaire et ou elle en est.
 *
 * Les trois compteurs accompagnent la seance parce que ce sont eux qu'on lit en premier : combien
 * d'articles a couvrir, combien sont faits, combien ne tombent pas juste. Les recalculer a
 * l'ecran obligerait a rapatrier toutes les lignes pour n'en afficher que trois nombres.
 */
public record SeanceInventaireDto(
        Long id,
        String reference,
        Instant dateOuverture,
        Instant dateCloture,
        StatutSeanceInventaire statut,
        String commentaire,
        Long idEntreprise,
        long articles,
        long comptes,
        long ecarts) {

    public static SeanceInventaireDto fromEntity(SeanceInventaire seance) {
        return fromEntity(seance, 0, 0, 0);
    }

    public static SeanceInventaireDto fromEntity(SeanceInventaire seance,
                                                 long articles, long comptes, long ecarts) {
        if (seance == null) {
            return null;
        }
        return new SeanceInventaireDto(
                seance.getId(),
                seance.getReference(),
                seance.getDateOuverture(),
                seance.getDateCloture(),
                seance.getStatut(),
                seance.getCommentaire(),
                seance.getIdEntreprise(),
                articles,
                comptes,
                ecarts);
    }
}
