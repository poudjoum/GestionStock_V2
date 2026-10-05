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

import java.time.LocalDate;

/**
 * Un lot de fabrication d'un article : son numero, tel qu'imprime sur l'emballage, et sa date.
 *
 * Il ne porte pas de quantite : son stock, dans chaque site, est la somme de ses mouvements.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = "article")
@Entity
@Table(name = "lot")
public class Lot extends AbstractEntity {

    @Column(name = "id_entreprise")
    private Long idEntreprise;

    @ManyToOne
    @JoinColumn(name = "id_article", nullable = false)
    private Article article;

    @Column(name = "numero", nullable = false, length = 60)
    private String numero;

    @Column(name = "date_peremption")
    private LocalDate datePeremption;

    /** La date est-elle passee ce jour-la ? Un lot sans date ne perime pas. */
    public boolean perimeLe(LocalDate jour) {
        return datePeremption != null && datePeremption.isBefore(jour);
    }
}
