package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.StatutAbonnement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Un commerce vu depuis la plateforme : ce qu'il est, et ce qu'il fait.
 *
 * C'est la ligne du tableau de bord de l'editeur, et elle repond a deux questions dans cet ordre :
 * cet abonnement est-il en regle, et ce client se sert-il de l'outil. La seconde decide de la
 * premiere bien plus souvent qu'on ne croit — un commerce qui n'entre plus ne renouvellera pas.
 */
public record CommerceDto(
        Long id,
        String nom,
        String ville,
        String tel,
        String email,
        LocalDate abonnementEcheance,
        boolean suspendue,
        StatutAbonnement statut,
        /** Les jours qui restent avant l'echeance. Negatif quand elle est passee, nul sans abonnement. */
        Integer joursRestants,
        long comptes,
        long articles,
        long ventes,
        BigDecimal chiffreFacture,
        /** La derniere vente enregistree : le signe de vie le plus simple qui soit. */
        Instant derniereVente) {
}
