package com.jumpy.tech.gestionstock.gestiondestock.dto;

import java.math.BigDecimal;

/**
 * Ce qu'on a trouve sur l'etagere, tel que l'ecran l'envoie.
 *
 * L'article se designe par son identifiant ou par son code-barres, et non par les deux : la
 * douchette ne connait que le code, la liste ne connait que l'identifiant. Le code l'emporte
 * quand les deux arrivent — c'est ce qui vient d'etre lu sur l'article qu'on tient en main.
 */
public record ComptageDto(Long idArticle, String codeArticle, BigDecimal quantite) {
}
