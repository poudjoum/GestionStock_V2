package com.jumpy.tech.gestionstock.gestiondestock.dto;

import lombok.Data;

/**
 * Ce qu'on dit en cessant d'attendre un reliquat.
 *
 * Un corps pour un seul champ plutot qu'un parametre de requete : le motif est du texte libre —
 * « fournisseur en rupture depuis mars, article remplace par le 4501 » — et un texte libre dans
 * l'URL finit encode a moitie et recopie dans les journaux d'acces de tout ce qui se trouve sur
 * le chemin.
 */
@Data
public class ClotureDto {

    private String motif;
}
