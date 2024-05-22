package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.VenteControllerApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.service.VenteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<VenteDto> findVenteByCode(String codeVente) {
        return ResponseEntity.status(HttpStatus.OK).body(venteService.findVenteByCode(codeVente));
    }

    @Override
    public ResponseEntity delete(Long id) {
        venteService.delete(id);
        return ResponseEntity.ok().build();
    }
}
