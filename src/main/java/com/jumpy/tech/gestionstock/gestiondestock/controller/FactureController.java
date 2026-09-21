package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.FactureApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FactureDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ReglementDto;
import com.jumpy.tech.gestionstock.gestiondestock.service.FactureService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    public ResponseEntity<Page<FactureDto>> findAll(String q, String statut, Pageable pageable) {
        return ResponseEntity.ok(factureService.rechercher(q, statut, pageable));
    }

    @Override
    public ResponseEntity<FactureDto> annuler(Long idFacture) {
        return ResponseEntity.ok(factureService.annuler(idFacture));
    }

    @Override
    public ResponseEntity<ReglementDto> regler(Long idFacture, ReglementDto reglement) {
        return ResponseEntity.ok(factureService.regler(idFacture, reglement));
    }

    @Override
    public ResponseEntity<List<ReglementDto>> reglements(Long idFacture) {
        return ResponseEntity.ok(factureService.reglements(idFacture));
    }

    @Override
    public ResponseEntity<Void> supprimerReglement(Long idFacture, Long idReglement) {
        factureService.supprimerReglement(idFacture, idReglement);
        return ResponseEntity.noContent().build();
    }
}
