package com.jumpy.tech.gestionstock.gestiondestock.dto;

import java.math.BigDecimal;

/** Un seuil d'alerte ; nul, il n'y en a pas. */
public record SeuilDto(BigDecimal seuil) {
}
