package com.jumpy.tech.gestionstock.gestiondestock.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Un ticket tel que le client le retrouve en scannant son QR : sans compte, depuis son telephone.
 *
 * Ce qu'il a achete, ou, quand, pour combien, et les points que cela lui vaut. Rien de plus : ni
 * son nom, ni le caissier, ni le numero de facture. Quelqu'un qui ramasserait le ticket d'un autre
 * n'apprend que ce que le papier disait deja.
 */
@Data
@Builder
public class TicketPublicDto {

    private String code;
    private String magasin;
    private String ville;
    private Instant date;
    private List<Ligne> articles;
    /** Nul tant que la facture n'est pas emise : le total qui fait foi est le sien. */
    private BigDecimal totalTtc;
    /** Les points que ce ticket rapporte ; zero si le commerce n'a pas de programme. */
    private int points;
    private boolean fideliteActive;
    /** Ce qu'il faut payer pour un point, pour que la page puisse l'expliquer. */
    private BigDecimal montantParPoint;
    /** Une vente annulee ne rapporte rien, et la page doit le dire plutot que de le taire. */
    private boolean annulee;

    @Data
    @Builder
    public static class Ligne {
        private String designation;
        private BigDecimal quantite;
        /** Ce que la ligne a coute au client, TTC quand la facture est emise. */
        private BigDecimal montant;
    }
}
