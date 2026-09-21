package com.jumpy.tech.gestionstock.gestiondestock.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Ce qui arrive reellement sur une ligne de commande.
 *
 * L'operation prend une liste : un fournisseur livre plusieurs articles d'un coup, et
 * enregistrer chacun separement multiplierait les etats intermediaires pour une seule arrivee.
 */
@Data
public class LigneReceptionDto {

    private Long idLigne;

    /** La quantite arrivee maintenant, pas le cumul : ce qui a ete compte au dechargement. */
    private BigDecimal quantite;
}
