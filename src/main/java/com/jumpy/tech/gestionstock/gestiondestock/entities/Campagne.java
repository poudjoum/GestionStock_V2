package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Une campagne de promotion d'un magasin.
 *
 * Une periode, du premier au dernier jour compris, a l'heure du magasin. Pendant ce temps ses
 * articles se vendent au prix reduit, et les tickets rapportent des points — a scanner avant la
 * fin de la campagne. Hors campagne, un ticket ne rapporte rien.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = "promotions")
@ToString(exclude = "promotions")
@Entity
@Table(name = "campagne")
public class Campagne extends AbstractEntity {

    @Column(name = "id_entreprise", nullable = false)
    private Long idEntreprise;

    @Column(name = "titre", nullable = false, length = 120)
    private String titre;

    /** Ce que lisent les clients dans l'application. */
    @Column(name = "message", length = 1000)
    private String message;

    /** Une image encodee, comme le logo du magasin. Facultative. */
    @Column(name = "image", columnDefinition = "text")
    private String image;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    /**
     * Arretee avant son terme. Les prix reprennent aussitot, et ses tickets ne se scannent plus.
     * Elle reste en base : des ventes ont ete faites a ses prix.
     */
    @Column(name = "arretee", nullable = false)
    private boolean arretee;

    @OneToMany(mappedBy = "campagne", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PromotionArticle> promotions = new ArrayList<>();

    /** Si la campagne vaut ce jour-la. */
    public boolean enCoursLe(LocalDate jour) {
        return !arretee && !jour.isBefore(dateDebut) && !jour.isAfter(dateFin);
    }
}
