package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.MvtStkControllerApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.service.MvtStkService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
public class MvtStkController implements MvtStkControllerApi {

    private final MvtStkService mvtStkService;

    public MvtStkController(MvtStkService mvtStkService) {
        this.mvtStkService = mvtStkService;
    }

    @Override
    public ResponseEntity<BigDecimal> stockReelArticle(Long idArticle) {
        return ResponseEntity.ok(mvtStkService.stockReelArticle(idArticle));
    }

    @Override
    public ResponseEntity<List<MvtStkDto>> mvtStkArticle(Long idArticle) {
        return ResponseEntity.ok(mvtStkService.mvtStkArticle(idArticle));
    }

    @Override
    public ResponseEntity<MvtStkDto> entreeStock(MvtStkDto dto) {
        return ResponseEntity.ok(mvtStkService.entreeStock(dto));
    }

    @Override
    public ResponseEntity<MvtStkDto> sortieStock(MvtStkDto dto) {
        return ResponseEntity.ok(mvtStkService.sortieStock(dto));
    }
}
