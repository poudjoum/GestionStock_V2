package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.dto.RapportImportDto;
import com.jumpy.tech.gestionstock.gestiondestock.service.ImportRepertoireService;
import com.jumpy.tech.gestionstock.gestiondestock.service.ImportRepertoireService.Repertoire;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

/**
 * L'import des clients et des fournisseurs, sur le modele de celui du catalogue : simulation par
 * defaut, ecriture sur demande, et un classeur modele a telecharger.
 */
@RestController
@Tag(name = "Post", description = "Post Methods of Gestion de Stock APIs")
public class ImportRepertoireController {

    private static final MediaType CLASSEUR = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ImportRepertoireService service;

    public ImportRepertoireController(ImportRepertoireService service) {
        this.service = service;
    }

    @Operation(summary = "Importer des clients depuis un classeur Excel",
            description = "Simule par defaut. Les fiches sont reconnues par leur telephone.")
    @PostMapping(value = APP_ROOT + "/clients/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RapportImportDto> importerClients(
            @RequestParam("fichier") MultipartFile fichier,
            @RequestParam(value = "simulation", defaultValue = "true") boolean simulation) {
        return ResponseEntity.ok(service.importer(Repertoire.CLIENTS, fichier, simulation));
    }

    @Operation(summary = "Importer des fournisseurs depuis un classeur Excel",
            description = "Simule par defaut. Les fiches sont reconnues par leur telephone.")
    @PostMapping(value = APP_ROOT + "/fournisseur/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RapportImportDto> importerFournisseurs(
            @RequestParam("fichier") MultipartFile fichier,
            @RequestParam(value = "simulation", defaultValue = "true") boolean simulation) {
        return ResponseEntity.ok(service.importer(Repertoire.FOURNISSEURS, fichier, simulation));
    }

    @GetMapping(value = APP_ROOT + "/clients/import/modele")
    public ResponseEntity<byte[]> modeleClients() {
        return modele(Repertoire.CLIENTS);
    }

    @GetMapping(value = APP_ROOT + "/fournisseur/import/modele")
    public ResponseEntity<byte[]> modeleFournisseurs() {
        return modele(Repertoire.FOURNISSEURS);
    }

    private ResponseEntity<byte[]> modele(Repertoire repertoire) {
        return ResponseEntity.ok()
                .contentType(CLASSEUR)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("modele-" + repertoire.pluriel + ".xlsx").build().toString())
                .body(service.modele(repertoire));
    }
}
