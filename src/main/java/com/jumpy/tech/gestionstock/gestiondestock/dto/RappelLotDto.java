package com.jumpy.tech.gestionstock.gestiondestock.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Tout ce qu'il faut pour rappeler un lot : ou il en reste, et a qui il a ete vendu.
 *
 * Les ventes de comptoir sont souvent anonymes : elles figurent quand meme, avec leur date et leur
 * ticket — c'est ce qu'on affiche en caisse pour que le client se signale.
 */
public record RappelLotDto(LotDto lot, List<StockSiteDto> stocks, List<VenteDuLot> ventes) {

    public record VenteDuLot(Long idVente, String code, String codeTicket, Instant date, String nomSite,
                             String client, String telephone, BigDecimal quantite) {
    }
}
