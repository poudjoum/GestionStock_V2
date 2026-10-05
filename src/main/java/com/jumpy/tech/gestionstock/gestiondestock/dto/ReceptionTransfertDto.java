package com.jumpy.tech.gestionstock.gestiondestock.dto;

import java.math.BigDecimal;

/**
 * Ce que le site d'arrivee a compte sur une ligne. Sans ligne dans la reception, tout ce qui est
 * parti est arrive ; une quantite plus faible demande son motif.
 */
public record ReceptionTransfertDto(Long idLigne, BigDecimal quantiteRecue, String motifEcart) {
}
