package com.jumpy.tech.gestionstock.gestiondestock.dto;

/**
 * Ce qu'on note en ouvrant une seance : « inventaire de fin de mois », « apres le cambriolage ».
 *
 * Facultatif, et c'est voulu : obliger a ecrire une phrase avant de pouvoir compter ferait taper
 * n'importe quoi. Ce qui compte, c'est la date et ce qu'on trouve.
 */
public record OuvertureInventaireDto(String commentaire) {
}
