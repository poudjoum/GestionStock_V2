package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper=true)
@Entity
@Table(name="vente")
public class Vente extends AbstractEntity{
    @Column(name="code")
    private String code;
    @Column(name="dateVente")
    private Instant datevente;
    @Column(name="commentaire")
    private String Commentaires;
    @Column(name="idEntreprise")
    private Long idEntreprise;
    /**
     * Une vente annulee n'est pas effacee : sa marchandise est retournee en magasin par un
     * mouvement de compensation, et la vente reste lisible. Supprimer la ligne ferait disparaitre
     * une recette encaissee puis rendue, que la caisse doit pourtant pouvoir retrouver.
     */
    @Column(name="annulee", nullable = false)
    private boolean annulee;
}
