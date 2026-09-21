package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.VenteControllerApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.service.VenteService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
@RestController
public class VenteController implements VenteControllerApi {

    private VenteService venteService;
    public VenteController(VenteService venteService){
        this.venteService=venteService;
    }

    @Override
    public ResponseEntity<VenteDto> save(VenteDto dto) {
        return ResponseEntity.status(HttpStatus.OK).body(venteService.save(dto));
    }

    @Override
    public ResponseEntity<VenteDto> findById(Long id) {
        return ResponseEntity.status(HttpStatus.OK).body(venteService.findById(id));
    }

    @Override
    public ResponseEntity<List<VenteDto>> findAll() {
        return ResponseEntity.status(HttpStatus.OK).body(venteService.findAll());
    }

    @Override
    public ResponseEntity<Page<VenteDto>> findAll(Pageable pageable) {
        return ResponseEntity.ok(venteService.findAll(pageable));
    }

    @Override
    public ResponseEntity<VenteDto> findVenteByCode(String codeVente) {
        return ResponseEntity.status(HttpStatus.OK).body(venteService.findVenteByCode(codeVente));
    }

    @Override
    public ResponseEntity<List<LigneVenteDto>> lignes(Long idVente) {
        return ResponseEntity.ok(venteService.lignes(idVente));
    }

    @Override
    public ResponseEntity<VenteDto> annuler(Long idVente) {
        return ResponseEntity.ok(venteService.annuler(idVente));
    }

    @Override
    public ResponseEntity<LigneVenteDto> modifierQuantite(Long idVente, Long idLigne, BigDecimal quantite) {
        return ResponseEntity.ok(venteService.modifierQuantite(idVente, idLigne, quantite));
    }

    @Override
    public ResponseEntity<Void> retirerLigne(Long idVente, Long idLigne) {
        venteService.retirerLigne(idVente, idLigne);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity delete(Long id) {
        venteService.delete(id);
        return ResponseEntity.ok().build();
    }
}
