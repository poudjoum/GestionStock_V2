package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

/** Un article mis en promotion par une campagne, et sa reduction. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"campagne", "article"})
@ToString(exclude = {"campagne", "article"})
@Entity
@Table(name = "promotion_article")
public class PromotionArticle extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_campagne", nullable = false)
    private Campagne campagne;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_article", nullable = false)
    private Article article;

    @Column(name = "id_entreprise", nullable = false)
    private Long idEntreprise;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_remise", nullable = false, length = 20)
    private TypeRemise typeRemise;

    @Column(name = "valeur", nullable = false)
    private BigDecimal valeur;
}
