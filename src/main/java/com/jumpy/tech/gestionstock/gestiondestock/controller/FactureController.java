package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.FactureApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FactureDto;
import com.jumpy.tech.gestionstock.gestiondestock.service.FactureService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FactureController implements FactureApi {

    private final FactureService factureService;

    public FactureController(FactureService factureService) {
        this.factureService = factureService;
    }

    @Override
    public ResponseEntity<FactureDto> emettre(Long idVente) {
        return ResponseEntity.ok(factureService.emettre(idVente));
    }

    @Override
    public ResponseEntity<FactureDto> factureDeLaVente(Long idVente) {
        return ResponseEntity.ok(factureService.findByVente(idVente));
    }

    @Override
    public ResponseEntity<FactureDto> findById(Long idFacture) {
        return ResponseEntity.ok(factureService.findById(idFacture));
    }

    @Override
    public ResponseEntity<FactureDto> findByNumero(String numero) {
        return ResponseEntity.ok(factureService.findByNumero(numero));
    }

    @Override
    public ResponseEntity<Page<FactureDto>> findAll(Pageable pageable) {
        return ResponseEntity.ok(factureService.findAll(pageable));
    }

    @Override
    public ResponseEntity<FactureDto> annuler(Long idFacture) {
        return ResponseEntity.ok(factureService.annuler(idFacture));
    }
}
