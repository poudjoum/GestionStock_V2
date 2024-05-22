package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.FournisseurControllerApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.service.FournisseurService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
@RestController
public class FournisseurController implements FournisseurControllerApi {

    private FournisseurService fournisseurService;

    public FournisseurController(FournisseurService fournisseurService) {
        this.fournisseurService = fournisseurService;
    }

    @Override
    public ResponseEntity<FournisseurDto> save(FournisseurDto dto) {
        return ResponseEntity.status(HttpStatus.OK).body(fournisseurService.save(dto));
    }

    @Override
    public ResponseEntity<FournisseurDto> findById(Long idFour) {
        return ResponseEntity.status(HttpStatus.OK).body(fournisseurService.findById(idFour));
    }

    @Override
    public ResponseEntity<FournisseurDto> findFournisseurByNom(String nomFournisseur) {
        return ResponseEntity.status(HttpStatus.OK).body(fournisseurService.findFournisseurByNom(nomFournisseur));
    }

    @Override
    public ResponseEntity<List<FournisseurDto>> findAll() {
        return ResponseEntity.status(HttpStatus.OK).body(fournisseurService.findAll());
    }

    @Override
    public ResponseEntity<FournisseurDto> delete(Long idFour) {

        fournisseurService.delete(idFour);
        return ResponseEntity.ok().build();
    }
}
