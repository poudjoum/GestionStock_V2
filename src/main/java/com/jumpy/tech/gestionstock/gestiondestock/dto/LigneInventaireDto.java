package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.StatutStock;
import com.jumpy.tech.gestionstock.gestiondestock.entities.UniteMesure;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

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

    /** En unites de base. */
    private BigDecimal quantite;
    private UniteMesure uniteBase;
    /**
     * Les conditionnements actifs de l'article : la quantite se lit aussi en cartons, et c'est en
     * cartons qu'on declare une casse ou qu'on compte une reserve.
     */
    private List<ConditionnementDto> conditionnements;
    /** Le site dont on lit le stock ; nul en vue « tous sites ». */
    private Long idSite;
    private String nomSite;
    /** En vue « tous sites » : ou est la marchandise, site par site. */
    private List<StockSiteDto> parSite;
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
