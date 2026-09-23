package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.ImportArticleControllerApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.RapportImportDto;
import com.jumpy.tech.gestionstock.gestiondestock.service.ImportArticleService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Voir {@link ImportArticleControllerApi}. */
@RestController
public class ImportArticleController implements ImportArticleControllerApi {

    /** Le type d'un classeur .xlsx, tel que l'attend un navigateur pour l'ouvrir dans un tableur. */
    private static final MediaType CLASSEUR = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ImportArticleService importArticleService;

    public ImportArticleController(ImportArticleService importArticleService) {
        this.importArticleService = importArticleService;
    }

    @Override
    public ResponseEntity<RapportImportDto> importer(MultipartFile fichier, boolean simulation) {
        return ResponseEntity.ok(importArticleService.importer(fichier, simulation));
    }

    @Override
    public ResponseEntity<byte[]> modele() {
        byte[] classeur = importArticleService.modele();
        return ResponseEntity.ok()
                .contentType(CLASSEUR)
                // `attachment` et non `inline` : ce fichier est fait pour etre enregistre et
                // rempli, pas regarde dans un onglet.
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("modele-articles.xlsx").build().toString())
                .body(classeur);
    }
}
