package com.jumpy.tech.gestionstock.gestiondestock.rapport;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Les ventes d'une periode, et ce qu'elles ont rapporte, comparees a la periode precedente et a la
 * meme periode l'an dernier.
 *
 * La marge ne porte que sur les lignes dont on connait le cout : `caCouvert` dit sur quel chiffre
 * elle est calculee, et `estimee` qu'une partie repose sur le cout moyen d'aujourd'hui — pour les
 * ventes d'avant que le cout soit fige a la vente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RapportVentesDto {
    private LocalDate debut;
    private LocalDate fin;
    /** JOUR ou MOIS : le pas de la courbe. */
    private String pas;
    /** Nul en vue « tous sites ». */
    private Long idSite;
    private String nomSite;

    private Indicateurs courant;
    private Indicateurs precedent;
    private Indicateurs anneePrecedente;

    /** La courbe de la periode, et celle de la periode precedente alignee point a point. */
    private List<PointVentes> serie;
    private List<PointVentes> seriePrecedente;

    private List<Repartition> parCategorie;
    private List<Repartition> parSite;
    private List<Repartition> parVendeur;
    /** 24 heures, de 0 a 23. */
    private List<PointVentes> parHeure;
    /** 7 jours, du lundi au dimanche. */
    private List<PointVentes> parJourSemaine;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Indicateurs {
        private LocalDate debut;
        private LocalDate fin;
        private BigDecimal chiffreAffaires;
        private BigDecimal marge;
        /** Le chiffre d'affaires des lignes dont le cout est connu : la base de la marge. */
        private BigDecimal caCouvert;
        /** Marge / CA couvert, en pourcentage ; nul sans cout connu. */
        private BigDecimal tauxMarge;
        private long tickets;
        private BigDecimal panierMoyen;
        /** Une partie de la marge repose sur le cout moyen actuel, et non sur un cout fige. */
        private boolean estimee;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PointVentes {
        /** « 2026-10-06 », « 2026-10 », « 14 » (heure), « 1 » (lundi). */
        private String cle;
        private BigDecimal chiffreAffaires;
        private BigDecimal marge;
        private long tickets;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Repartition {
        private Long id;
        private String libelle;
        private BigDecimal chiffreAffaires;
        private BigDecimal marge;
        private BigDecimal tauxMarge;
        private long tickets;
        /** Part du chiffre d'affaires de la periode, en pourcentage. */
        private BigDecimal part;
    }
}
