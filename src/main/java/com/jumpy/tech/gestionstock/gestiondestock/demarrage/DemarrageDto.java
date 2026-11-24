package com.jumpy.tech.gestionstock.gestiondestock.demarrage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Ou en est un commerce de ses premiers pas.
 *
 * Le serveur ne rend que des faits — quelle etape est faite, combien d'elements elle compte. Les
 * mots qui les accompagnent, et l'ecran ou on les fait, sont l'affaire du front.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemarrageDto {

    /** Les etapes, dans l'ordre ou on les conseille. */
    private List<EtapeDto> etapes;

    /** Toutes les etapes obligatoires sont faites. */
    private boolean termine;

    /** Le gerant a ferme l'assistant : l'accueil ne le montre plus. */
    private boolean masque;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EtapeDto {
        private EtapeDemarrage code;
        private boolean faite;
        /** Une etape facultative ne retient pas l'assistant : un commerce peut n'avoir aucun employe. */
        private boolean facultative;
        /** Ce que l'etape compte deja (categories, articles, collaborateurs). Nul quand compter n'a pas de sens. */
        private Long nombre;
    }
}
