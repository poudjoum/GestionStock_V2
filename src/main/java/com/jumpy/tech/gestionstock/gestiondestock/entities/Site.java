package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Un lieu ou se tient du stock : un magasin, ou un entrepot.
 *
 * Le stock d'un article dans un site est la somme des mouvements de ce site. Chaque entreprise en
 * a au moins un, son site principal, cree avec elle.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "site")
public class Site extends AbstractEntity {

    @Column(name = "id_entreprise", nullable = false)
    private Long idEntreprise;

    @Column(name = "nom", nullable = false, length = 80)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_site", nullable = false, length = 10)
    private TypeSite type;

    @Column(name = "adresse")
    private String adresse;

    @Column(name = "telephone", length = 30)
    private String telephone;

    @Column(name = "principal", nullable = false)
    private boolean principal;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    public boolean vend() {
        return type == TypeSite.MAGASIN;
    }
}
