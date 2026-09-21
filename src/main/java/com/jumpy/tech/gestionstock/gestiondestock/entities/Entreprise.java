package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper=true)
@Entity
@Table(name="Entreprise")

public class Entreprise extends AbstractEntity{
    @Column(name="Nom")
    private String nom;
    @Column(name="Description")
    private String description;
    @Embedded
    private Adresse adresse;
    @Column(name="RegistreCommerce")
    private String registreCommerce;
    @Column(name="Email")
    private String email_Entreprise;

    @Column(name="telephone")
    private String tel;
    @Column(name="Siteweb")
    private String siteWeb;
    /**
     * Si l'entreprise collecte la TVA. Toutes ne le font pas : certaines la reversent aux impots
     * par declaration, d'autres n'y sont pas soumises. Quand c'est faux, aucune ligne de facture
     * ne porte de TVA, quel que soit l'article.
     */
    @Column(name="assujettie_tva", nullable = false)
    private boolean assujettieTva = true;
    /** Le taux applique par defaut aux articles qui n'en fixent pas un a eux. */
    @Column(name="taux_tva", nullable = false)
    private BigDecimal tauxTva;
    @OneToMany(mappedBy="entreprise")
    private List<Utilisateur> users;
}
