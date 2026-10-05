package com.jumpy.tech.gestionstock.gestiondestock.analyse;

import com.jumpy.tech.gestionstock.gestiondestock.entities.UniteMesure;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Ce que rapporte chaque article sur une periode, et ce que son stock immobilise.
 *
 * La classe ABC range les articles par chiffre d'affaires : A, ceux qui font les premiers 80 % ;
 * B, les 15 % suivants ; C, le reste — souvent la moitie du catalogue pour 5 % des ventes. C'est la
 * liste des A qu'on ne doit jamais laisser tomber en rupture.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyseArticlesDto {
    private int jours;
    /** Nul en vue « tous sites ». */
    private Long idSite;
    private String nomSite;
    private BigDecimal chiffreAffaires;
    private long nombreA;
    private long nombreB;
    private long nombreC;
    /** Du stock, et pas une vente sur la periode. */
    private long nombreDormants;
    /** Ce que valent les dormants au cout d'achat moyen ; les articles sans cout connu n'y sont pas. */
    private BigDecimal valeurDormante;
    private List<LigneAnalyse> articles;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LigneAnalyse {
        private Long idArticle;
        private String codeArticle;
        private String designation;
        private UniteMesure uniteBase;
        private BigDecimal chiffreAffaires;
        private BigDecimal quantiteVendue;
        /** Part du chiffre d'affaires, et part cumulee jusqu'a cet article, en pourcentage. */
        private BigDecimal part;
        private BigDecimal partCumulee;
        /** A, B ou C. */
        private String classe;
        private BigDecimal stock;
        /** Combien de jours le stock tient au rythme de la periode ; nul sans vente. */
        private BigDecimal couvertureJours;
        private boolean dormant;
        private BigDecimal valeurStock;
        private Instant derniereVente;
    }
}
