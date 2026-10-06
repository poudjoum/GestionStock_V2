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
 * Ou le commerce perd de l'argent : la demarque de la periode, les factures que les clients
 * doivent encore, et le stock qui dort.
 *
 * La demarque porte sur une periode ; les impayes et le stock dormant, sur aujourd'hui — ce qu'on
 * doit encore, c'est ce qu'on doit maintenant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RapportPertesDto {
    private LocalDate debut;
    private LocalDate fin;
    private Long idSite;
    private String nomSite;

    private Demarque demarque;
    private Impayes impayes;
    private Dormants dormants;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Demarque {
        /** Au cout d'achat ; un surplus d'inventaire vient en deduction. */
        private BigDecimal valeur;
        private BigDecimal valeurPrecedente;
        /** Valeur / chiffre d'affaires HT de la periode, en pourcentage ; nul sans vente. */
        private BigDecimal tauxDuChiffre;
        private BigDecimal chiffreAffaires;
        /** Une partie est valorisee au cout moyen actuel : des mouvements d'avant le cout fige. */
        private boolean estimee;
        /** Des mouvements d'articles au cout inconnu, qui ne sont donc pas valorises. */
        private long sansCout;
        private List<Poste> parMotif;
        private List<Poste> parArticle;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Poste {
        /** CASSE, PERTE, PEREMPTION, CONSOMMATION_INTERNE, INVENTAIRE_MANQUANT, INVENTAIRE_SURPLUS ; ou l'article. */
        private String cle;
        private String libelle;
        private BigDecimal valeur;
        private BigDecimal quantite;
        private long mouvements;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Impayes {
        private BigDecimal total;
        private long factures;
        private long clients;
        /** 0-30, 31-60, 61-90, plus de 90 jours depuis l'emission. */
        private List<Tranche> parAnciennete;
        private List<ClientDebiteur> parClient;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tranche {
        private String libelle;
        private BigDecimal montant;
        private long factures;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientDebiteur {
        /** Nul pour une facture au nom d'un client de passage. */
        private Long idClient;
        private String nom;
        private String telephone;
        private BigDecimal du;
        private long factures;
        private Instant plusAncienne;
        private long joursDeRetard;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Dormants {
        private int jours;
        private long nombre;
        private BigDecimal valeur;
        private List<ArticleDormant> articles;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ArticleDormant {
        private Long idArticle;
        private String designation;
        private BigDecimal stock;
        private BigDecimal valeur;
    }
}
