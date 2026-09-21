package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.CaisseApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EtatDeCaisseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ReglementDto;
import com.jumpy.tech.gestionstock.gestiondestock.service.CaisseService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class CaisseController implements CaisseApi {

    private final CaisseService caisseService;

    public CaisseController(CaisseService caisseService) {
        this.caisseService = caisseService;
    }

    @Override
    public ResponseEntity<EtatDeCaisseDto> etat(LocalDate debut, LocalDate fin) {
        return ResponseEntity.ok(caisseService.etat(debut, fin));
    }

    @Override
    public ResponseEntity<Page<ReglementDto>> reglements(LocalDate debut, LocalDate fin, Pageable pageable) {
        return ResponseEntity.ok(caisseService.reglements(debut, fin, pageable));
    }
}
