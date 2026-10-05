package com.jumpy.tech.gestionstock.gestiondestock.reappro;

import com.jumpy.tech.gestionstock.gestiondestock.dto.ConditionnementDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.UniteMesure;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * La proposition de reapprovisionnement d'un site : ce qu'il faudrait commander pour tenir
 * `joursCouverture` jours au rythme des ventes des `joursObserves` derniers jours.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReapproDto {
    private Long idSite;
    private String nomSite;
    private int joursCouverture;
    private int joursObserves;
    private List<LigneReappro> lignes;

    /** Un article a recommander, et pourquoi. Les quantites de stock sont en unites de base. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LigneReappro {
        private Long idArticle;
        private String codeArticle;
        private String designation;
        private UniteMesure uniteBase;

        private BigDecimal stock;
        private BigDecimal reserve;
        private BigDecimal disponible;
        /** Deja commande et pas encore arrive, brouillons compris. */
        private BigDecimal enCommande;
        private BigDecimal seuil;

        /** Vendu sur la periode observee, et par jour. */
        private BigDecimal vendu;
        private BigDecimal parJour;
        /** Combien de jours le disponible tient au rythme actuel ; nul sans vente. */
        private BigDecimal couvertureJours;

        /** Ce qui manque pour tenir, en unites de base. */
        private BigDecimal besoin;

        /** Le fournisseur et l'unite du dernier achat ; nuls pour un article jamais achete. */
        private FournisseurDto fournisseur;
        private ConditionnementDto conditionnement;
        /** Unites de base par unite proposee (1 a l'unite de base). */
        private BigDecimal contenance;
        /** La quantite proposee, dans l'unite d'achat, arrondie a l'unite entiere au-dessus. */
        private BigDecimal quantiteProposee;
        /** Le prix du dernier achat, par unite d'achat. */
        private BigDecimal prixAchat;

        /** RUPTURE, SOUS_SEUIL ou A_COUVRIR. */
        private String raison;
    }
}
