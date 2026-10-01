package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Le compte d'un client de l'application mobile de fidelite.
 *
 * Identifie par son numero de telephone. N'appartient a aucune entreprise en particulier : le client
 * fait ses courses dans plusieurs commerces du reseau abonnes au service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "compte_client_fidelite")
public class CompteClientFidelite extends AbstractEntity {

    @Column(name = "telephone", nullable = false, unique = true, length = 30)
    private String telephone;

    @Column(name = "nom", length = 100)
    private String nom;

    @Column(name = "prenom", length = 100)
    private String prenom;

    @Column(name = "mot_de_passe", nullable = false)
    private String motDePasse;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;
}
