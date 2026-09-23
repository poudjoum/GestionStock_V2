package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.EntrepriseControllerApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.InscriptionEntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.service.EntrepriseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
@RestController
public class EntrepriseController implements EntrepriseControllerApi {
    private EntrepriseService entrepriseService;


    public EntrepriseController(EntrepriseService es){
        this.entrepriseService=es;
    }

    @Override
    public ResponseEntity<EntrepriseDto> save(EntrepriseDto dto) {
        return ResponseEntity.status(HttpStatus.OK).body(entrepriseService.save(dto));
    }

    @Override
    public ResponseEntity<EntrepriseDto> inscrire(InscriptionEntrepriseDto inscription) {
        return ResponseEntity.ok(entrepriseService.inscrire(inscription));
    }

    @Override
    public ResponseEntity<EntrepriseDto> mienne() {
        return ResponseEntity.ok(entrepriseService.mienne());
    }

    @Override
    public ResponseEntity<EntrepriseDto> findById(Long idEntreprise) {
        return ResponseEntity.status(HttpStatus.OK).body(entrepriseService.findById(idEntreprise));
    }

    @Override
    public ResponseEntity<List<EntrepriseDto>> findAll() {
        return ResponseEntity.status(HttpStatus.OK).body(entrepriseService.findAll());
    }

    @Override
    public ResponseEntity delete(Long idEntreprise) {
        entrepriseService.delete(idEntreprise);
        return ResponseEntity.ok().build();
    }
}
