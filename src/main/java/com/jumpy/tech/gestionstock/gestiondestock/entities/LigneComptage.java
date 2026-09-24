package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un article dans une seance de comptage.
 *
 * `LigneComptage` et non `LigneInventaire` : ce dernier nom designe deja la vue de valorisation du
 * magasin — combien vaut ce qu'on a — qui ne compte rien du tout.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "ligne_comptage")
public class LigneComptage extends AbstractEntity {

    @ManyToOne
    @JoinColumn(name = "id_seance", nullable = false)
    private SeanceInventaire seance;

    @ManyToOne
    @JoinColumn(name = "id_article", nullable = false)
    private Article article;

    /**
     * Le code et la designation recopies a l'ouverture, comme sur une ligne de facture : un
     * article renomme apres coup ne doit pas rendre illisible un inventaire passe.
     */
    @Column(name = "code_article")
    private String codeArticle;

    @Column(name = "designation")
    private String designation;

    /**
     * Ce que le logiciel croyait avoir au moment d'ouvrir la seance.
     *
     * Fige : sans cela, l'ecart affiche bougerait sous les yeux de celui qui compte, a chaque
     * vente passee au comptoir pendant qu'il est dans les rayons.
     */
    @Column(name = "quantite_theorique", nullable = false)
    private BigDecimal quantiteTheorique;

    /**
     * Ce qu'on a trouve sur l'etagere. Nulle tant que la ligne n'a pas ete comptee.
     *
     * La distinction porte : zero compte veut dire « il n'y en a plus », ce qui n'est pas la meme
     * chose que « pas encore regarde » — et les deux appellent des corrections opposees.
     */
    @Column(name = "quantite_comptee")
    private BigDecimal quantiteComptee;

    /**
     * Le stock du logiciel a l'instant precis ou la ligne a ete comptee.
     *
     * C'est lui, et non le theorique fige, qui sert a calculer la correction. Un magasin qui
     * compte sans fermer sa porte continue de vendre : corriger vers le theorique de l'ouverture
     * effacerait les ventes survenues entre-temps. La correction vaut « compte moins stock au
     * comptage », et les mouvements posterieurs s'appliquent par-dessus — ce qui est exact que la
     * boutique soit fermee ou non.
     */
    @Column(name = "stock_au_comptage")
    private BigDecimal stockAuComptage;

    @Column(name = "compte_le")
    private Instant compteLe;

    @Column(name = "id_entreprise")
    private Long idEntreprise;
}
