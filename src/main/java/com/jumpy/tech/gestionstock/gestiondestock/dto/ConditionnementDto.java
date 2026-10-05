package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Conditionnement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Un conditionnement d'article : « Carton de 24 », 24 unites de base, 9 000 HT.
 *
 * Sur une ligne de vente ou de commande, seul `id` est lu : la contenance et le prix viennent du
 * conditionnement enregistre, jamais de ce que dit la requete.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConditionnementDto {
    private Long id;
    private Long idArticle;
    private String libelle;
    private BigDecimal quantiteUnites;
    private BigDecimal prixVenteHt;
    private Boolean vendable;
    private Boolean achetable;
    private Boolean actif;

    public static ConditionnementDto fromEntity(Conditionnement conditionnement) {
        if (conditionnement == null) {
            return null;
        }
        return ConditionnementDto.builder()
                .id(conditionnement.getId())
                .idArticle(conditionnement.getArticle() == null ? null : conditionnement.getArticle().getId())
                .libelle(conditionnement.getLibelle())
                .quantiteUnites(conditionnement.getQuantiteUnites())
                .prixVenteHt(conditionnement.getPrixVenteHt())
                .vendable(conditionnement.isVendable())
                .achetable(conditionnement.isAchetable())
                .actif(conditionnement.isActif())
                .build();
    }
}
