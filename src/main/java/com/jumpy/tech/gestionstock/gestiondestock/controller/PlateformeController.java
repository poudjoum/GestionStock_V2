package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.PlateformeApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommerceDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ResumePlateformeDto;
import com.jumpy.tech.gestionstock.gestiondestock.service.PlateformeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
public class PlateformeController implements PlateformeApi {

    private final PlateformeService plateformeService;

    public PlateformeController(PlateformeService plateformeService) {
        this.plateformeService = plateformeService;
    }

    @Override
    public ResponseEntity<List<CommerceDto>> commerces() {
        return ResponseEntity.ok(plateformeService.commerces());
    }

    @Override
    public ResponseEntity<ResumePlateformeDto> resume() {
        return ResponseEntity.ok(plateformeService.resume());
    }

    @Override
    public ResponseEntity<CommerceDto> suspendre(Long idCommerce) {
        return ResponseEntity.ok(plateformeService.suspendre(idCommerce));
    }

    @Override
    public ResponseEntity<CommerceDto> reprendre(Long idCommerce) {
        return ResponseEntity.ok(plateformeService.reprendre(idCommerce));
    }

    @Override
    public ResponseEntity<CommerceDto> renouveler(Long idCommerce) {
        return ResponseEntity.ok(plateformeService.renouveler(idCommerce));
    }

    @Override
    public ResponseEntity<CommerceDto> fixerEcheance(Long idCommerce, LocalDate echeance) {
        return ResponseEntity.ok(plateformeService.fixerEcheance(idCommerce, echeance));
    }
}
