package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.StockApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EtatDuStockDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneInventaireDto;
import com.jumpy.tech.gestionstock.gestiondestock.service.StockService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class StockController implements StockApi {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @Override
    public ResponseEntity<EtatDuStockDto> etat(boolean tousSites) {
        return ResponseEntity.ok(stockService.etat(tousSites));
    }

    @Override
    public ResponseEntity<Page<LigneInventaireDto>> inventaire(String q, Pageable pageable, boolean tousSites) {
        return ResponseEntity.ok(stockService.inventaire(q, pageable, tousSites));
    }

    @Override
    public ResponseEntity<List<LigneInventaireDto>> alertes(boolean tousSites) {
        return ResponseEntity.ok(stockService.alertes(tousSites));
    }
}
