package com.jumpy.tech.gestionstock.gestiondestock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketHistoriqueDto {
    private String codeTicket;
    private Long idEntreprise;
    private String nomMagasin;
    private BigDecimal montantAchatTtc;
    private int pointsAttribues;
    private Instant dateReclamation;
}
