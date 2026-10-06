package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.rapport.RapportPertesDto;
import com.jumpy.tech.gestionstock.gestiondestock.rapport.RapportPertesService;
import com.jumpy.tech.gestionstock.gestiondestock.rapport.RapportVentesDto;
import com.jumpy.tech.gestionstock.gestiondestock.rapport.RapportVentesService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

@RestController
public class RapportController {

    private final RapportVentesService rapportVentesService;
    private final RapportPertesService rapportPertesService;

    public RapportController(RapportVentesService rapportVentesService, RapportPertesService rapportPertesService) {
        this.rapportVentesService = rapportVentesService;
        this.rapportPertesService = rapportPertesService;
    }

    /** Les ventes du `debut` au `fin` inclus, comparees a la periode precedente et a l'an dernier. */
    @Tag(name = "Get", description = "Get Methods of Gestion de Stock APIs")
    @GetMapping(value = APP_ROOT + "/rapports/ventes", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RapportVentesDto> ventes(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(defaultValue = "false") boolean tousSites) {
        return ResponseEntity.ok(rapportVentesService.ventes(debut, fin, tousSites));
    }

    /** La demarque de la periode, et ce que les clients doivent et ce qui dort aujourd'hui. */
    @GetMapping(value = APP_ROOT + "/rapports/pertes", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RapportPertesDto> pertes(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(defaultValue = "false") boolean tousSites) {
        return ResponseEntity.ok(rapportPertesService.pertes(debut, fin, tousSites));
    }
}
