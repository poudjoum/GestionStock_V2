package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeSite;

import java.math.BigDecimal;

/** Ce qu'un site a d'un article. */
public record StockSiteDto(Long idSite, String nomSite, TypeSite type, BigDecimal quantite) {
}
