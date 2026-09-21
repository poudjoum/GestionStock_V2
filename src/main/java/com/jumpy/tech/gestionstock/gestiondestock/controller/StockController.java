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
    public ResponseEntity<EtatDuStockDto> etat() {
        return ResponseEntity.ok(stockService.etat());
    }

    @Override
    public ResponseEntity<Page<LigneInventaireDto>> inventaire(Pageable pageable) {
        return ResponseEntity.ok(stockService.inventaire(pageable));
    }

    @Override
    public ResponseEntity<List<LigneInventaireDto>> alertes() {
        return ResponseEntity.ok(stockService.alertes());
    }
}
