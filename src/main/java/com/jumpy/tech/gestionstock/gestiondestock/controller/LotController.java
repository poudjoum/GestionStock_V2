package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.LotApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LotDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.RappelLotDto;
import com.jumpy.tech.gestionstock.gestiondestock.lot.LotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class LotController implements LotApi {

    private final LotService lotService;

    public LotController(LotService lotService) {
        this.lotService = lotService;
    }

    @Override
    public ResponseEntity<List<LotDto>> lotsDeLArticle(Long idArticle) {
        return ResponseEntity.ok(lotService.lotsDeLArticle(idArticle));
    }

    @Override
    public ResponseEntity<List<LotDto>> peremption(boolean tousSites) {
        return ResponseEntity.ok(lotService.peremption(tousSites));
    }

    @Override
    public ResponseEntity<RappelLotDto> rappel(Long idLot) {
        return ResponseEntity.ok(lotService.rappel(idLot));
    }
}
