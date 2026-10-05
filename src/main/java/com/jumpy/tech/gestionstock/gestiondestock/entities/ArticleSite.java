package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * Ce qui, pour un article, depend du site : son seuil d'alerte. Sans ligne, le seuil de l'article
 * s'applique.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"article", "site"})
@Entity
@Table(name = "article_site")
public class ArticleSite extends AbstractEntity {

    @ManyToOne
    @JoinColumn(name = "id_article", nullable = false)
    private Article article;

    @ManyToOne
    @JoinColumn(name = "id_site", nullable = false)
    private Site site;

    @Column(name = "id_entreprise")
    private Long idEntreprise;

    @Column(name = "seuil_alerte")
    private BigDecimal seuilAlerte;
}
