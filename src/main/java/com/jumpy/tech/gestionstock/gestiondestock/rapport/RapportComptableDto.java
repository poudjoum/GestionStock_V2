package com.jumpy.tech.gestionstock.gestiondestock.rapport;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Ce que le comptable demande chaque mois : la TVA collectee par taux, le journal des ventes et
 * celui des encaissements.
 *
 * Tout vient des factures, documents legaux qui figent HT, TVA et TTC a l'emission — et non des
 * ventes, que le prix ou le taux d'un article modifie depuis ne doivent pas reecrire. Rien n'est
 * arrondi : le comptable rapproche au centime.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RapportComptableDto {
    private LocalDate debut;
    private LocalDate fin;
    private Long idSite;
    private String nomSite;

    private List<LigneTva> tva;
    private BigDecimal totalHt;
    private BigDecimal totalTva;
    private BigDecimal totalTtc;
    private long factures;
    /** Annulees sur la periode : elles restent au journal, sans montant. */
    private long facturesAnnulees;

    private BigDecimal totalEncaisse;
    private List<EncaissementParMode> encaissementsParMode;

    private List<LigneJournalVentes> journalVentes;
    private List<LigneJournalEncaissements> journalEncaissements;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LigneTva {
        private String libelle;
        /** Nul quand la TVA ne s'applique pas. */
        private BigDecimal taux;
        private BigDecimal baseHt;
        private BigDecimal tva;
        private BigDecimal ttc;
        private long factures;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EncaissementParMode {
        private String mode;
        private String libelle;
        private BigDecimal montant;
        private long nombre;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LigneJournalVentes {
        private Instant date;
        private String numero;
        private String client;
        private BigDecimal totalHt;
        private BigDecimal totalTva;
        private BigDecimal totalTtc;
        private BigDecimal regle;
        private BigDecimal reste;
        private boolean annulee;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LigneJournalEncaissements {
        private Instant date;
        private String numeroFacture;
        private String client;
        private String mode;
        private BigDecimal montant;
        private String reference;
    }
}
