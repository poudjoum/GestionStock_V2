package com.jumpy.tech.gestionstock.gestiondestock.reappro;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ce que le gerant a retenu de la proposition : une ligne par article, avec le fournisseur choisi.
 * Il en sort une commande en preparation par fournisseur.
 */
public record CommandesReapproDto(List<LigneCommandeReappro> lignes) {

    /** `idConditionnement` nul : la quantite est en unites de base. */
    public record LigneCommandeReappro(Long idArticle, Long idFournisseur, Long idConditionnement, BigDecimal quantite,
                        BigDecimal prixUnitaire) {
    }
}
