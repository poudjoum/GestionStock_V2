package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name="article")

public class Article extends AbstractEntity{
    private String codeArticle;
    private String designation;
    private BigDecimal prixUnitaire;
    @Column(name="TauxTVA")
    private BigDecimal tauxTva;
    @Column(name="prixUnitTTC")
    private BigDecimal prixUnitTTC;
    @Column(name="photo")
    private String photo;
    /**
     * La quantite sous laquelle il faut recommander. Nul quand l'article ne merite pas d'alerte :
     * un seuil impose partout noierait les vraies alertes sous des dizaines de fausses.
     */
    @Column(name="seuil_alerte")
    private BigDecimal seuilAlerte;

    /** L'unite dans laquelle le stock est tenu ; ses conditionnements en sont des multiples. */
    @Enumerated(EnumType.STRING)
    @Column(name="unite_base", nullable = false, length = 10)
    private UniteMesure uniteBase = UniteMesure.PIECE;

    /** Suivi par lot : chaque reception porte un numero de lot et, s'il y a lieu, une date. */
    @Column(name="suivi_lot", nullable = false)
    private boolean suiviLot;

    /** La date que portent ses lots ; nulle, ils n'en ont pas. */
    @Enumerated(EnumType.STRING)
    @Column(name="type_date", length = 4)
    private TypeDate typeDate;

    /** Combien de jours avant la date prevenir ; nul, le reglage du magasin. */
    @Column(name="delai_alerte_peremption")
    private Integer delaiAlertePeremption;
    @Column(name="idEntreprise")
    private Long idEntreprise;
    @ManyToOne
    @JoinColumn(name="idCategory")
    private Category category;
    @OneToMany(mappedBy = "articles")
    private List<LigneVente> ligneVentes;
    @OneToMany(mappedBy = "articles")
    private List<LigneCmndeClient> ligneCmndeClients;
    @OneToMany(mappedBy = "articles")
    private List<MvtStk>mvtStks;
}
