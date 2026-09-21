package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.StatutStock;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Un article et ce qu'il en reste.
 *
 * Deux valorisations, parce qu'elles ne repondent pas a la meme question : ce que la marchandise a
 * coute, et ce qu'elle rapportera si elle se vend. Confondre les deux fait passer une marge pour
 * un avoir.
 */
@Builder
@Data
public class LigneInventaireDto {

    private Long idArticle;
    private String codeArticle;
    private String designation;

    private BigDecimal quantite;
    private BigDecimal seuilAlerte;
    private StatutStock statut;

    /** Cout d'achat moyen, deduit des commandes fournisseur livrees. Nul si l'article n'a jamais
     * ete achete par une commande — approvisionne a la main, par exemple. */
    private BigDecimal coutMoyenAchat;

    /** Quantite x cout moyen. Nul quand le cout est inconnu : mieux vaut l'avouer que l'inventer. */
    private BigDecimal valeurAuCout;

    private BigDecimal prixUnitaireVente;

    /** Quantite x prix de vente : ce que la marchandise rapporterait, pas ce qu'elle vaut. */
    private BigDecimal valeurAuPrixDeVente;
}
