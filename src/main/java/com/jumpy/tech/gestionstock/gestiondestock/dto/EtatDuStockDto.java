package com.jumpy.tech.gestionstock.gestiondestock.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Ce que vaut le magasin, et ce qui y manque.
 *
 * Le pendant de l'etat de caisse, cote marchandise : la caisse dit ce qui est entre, celui-ci dit
 * ce qui dort en rayon.
 */
@Builder
@Data
public class EtatDuStockDto {

    private long nombreArticles;

    /** Articles dont il ne reste rien, ou moins que rien. */
    private long nombreEnRupture;

    /** Articles passes sous leur seuil, sans y etre encore tombes a zero. */
    private long nombreSousSeuil;

    /**
     * Valeur au cout d'achat, somme des seuls articles dont le cout est connu.
     *
     * C'est le chiffre comptable : ce que la marchandise en rayon a coute.
     */
    private BigDecimal valeurAuCout;

    /**
     * Combien d'articles n'ont pas de cout connu, et ne comptent donc pas dans la valeur
     * ci-dessus. Sans ce nombre, une valorisation partielle passerait pour complete.
     */
    private long nombreSansCoutConnu;

    /** Ce que le stock rapporterait s'il se vendait entierement. Ce n'est pas ce qu'il vaut. */
    private BigDecimal valeurAuPrixDeVente;
}
