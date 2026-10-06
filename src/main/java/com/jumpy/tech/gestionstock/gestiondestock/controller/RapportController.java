package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.promotion.Calendrier;
import com.jumpy.tech.gestionstock.gestiondestock.rapport.ExportComptable;
import com.jumpy.tech.gestionstock.gestiondestock.rapport.RapportComptableDto;
import com.jumpy.tech.gestionstock.gestiondestock.rapport.RapportComptableService;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EntrepriseRepository;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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

    private final RapportComptableService rapportComptableService;
    private final ExportComptable exportComptable;
    private final EntrepriseRepository entrepriseRepository;
    private final Cloisonnement cloisonnement;
    private final Calendrier calendrier;

    public RapportController(RapportVentesService rapportVentesService, RapportPertesService rapportPertesService,
                             RapportComptableService rapportComptableService, ExportComptable exportComptable,
                             EntrepriseRepository entrepriseRepository, Cloisonnement cloisonnement,
                             Calendrier calendrier) {
        this.rapportVentesService = rapportVentesService;
        this.rapportPertesService = rapportPertesService;
        this.rapportComptableService = rapportComptableService;
        this.exportComptable = exportComptable;
        this.entrepriseRepository = entrepriseRepository;
        this.cloisonnement = cloisonnement;
        this.calendrier = calendrier;
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

    /** La TVA collectee par taux et les journaux de la periode, lus sur les factures. */
    @GetMapping(value = APP_ROOT + "/rapports/comptabilite", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RapportComptableDto> comptabilite(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(defaultValue = "false") boolean tousSites) {
        return ResponseEntity.ok(rapportComptableService.comptabilite(debut, fin, tousSites));
    }

    /** Le meme rapport en classeur Excel : synthese, journal des ventes, encaissements. */
    @GetMapping(value = APP_ROOT + "/rapports/comptabilite.xlsx",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> comptabiliteExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(defaultValue = "false") boolean tousSites) {
        RapportComptableDto rapport = rapportComptableService.comptabilite(debut, fin, tousSites);
        Long idEntreprise = cloisonnement.entrepriseCourante();
        var entreprise = idEntreprise == null ? null : entrepriseRepository.findById(idEntreprise).orElse(null);
        byte[] classeur = exportComptable.classeur(rapport, entreprise, calendrier.fuseau());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(ExportComptable.nomDeFichier(entreprise, rapport), java.nio.charset.StandardCharsets.UTF_8)
                        .build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(classeur);
    }
}
