package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Un code lu par la douchette ou la camera, et ce qu'il designe : un article, a l'unite ou dans
 * l'un de ses conditionnements.
 *
 * Le code de la bouteille et celui du carton ne sont pas les memes. C'est ce qui permet au scan
 * d'ajouter un carton quand on lui presente un carton, sans que le caissier ait rien a choisir.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"article", "conditionnement"})
@Entity
@Table(name = "code_barres")
public class CodeBarres extends AbstractEntity {

    @Column(name = "id_entreprise")
    private Long idEntreprise;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_code", nullable = false, length = 10)
    private TypeCodeBarres type;

    @ManyToOne
    @JoinColumn(name = "id_article", nullable = false)
    private Article article;

    /** Nul : le code designe l'unite de base. */
    @ManyToOne
    @JoinColumn(name = "id_conditionnement")
    private Conditionnement conditionnement;
}
