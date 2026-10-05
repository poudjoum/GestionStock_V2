package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.ConditionnementApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CodeBarresDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ConditionnementDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ResultatScanDto;
import com.jumpy.tech.gestionstock.gestiondestock.service.ConditionnementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ConditionnementController implements ConditionnementApi {

    private final ConditionnementService conditionnementService;

    public ConditionnementController(ConditionnementService conditionnementService) {
        this.conditionnementService = conditionnementService;
    }

    @Override
    public ResponseEntity<List<ConditionnementDto>> conditionnements(Long idArticle) {
        return ResponseEntity.ok(conditionnementService.conditionnements(idArticle));
    }

    @Override
    public ResponseEntity<ConditionnementDto> ajouter(Long idArticle, ConditionnementDto dto) {
        return ResponseEntity.ok(conditionnementService.ajouter(idArticle, dto));
    }

    @Override
    public ResponseEntity<ConditionnementDto> modifier(Long idArticle, Long idConditionnement, ConditionnementDto dto) {
        return ResponseEntity.ok(conditionnementService.modifier(idArticle, idConditionnement, dto));
    }

    @Override
    public ResponseEntity<Void> retirer(Long idArticle, Long idConditionnement) {
        conditionnementService.retirer(idArticle, idConditionnement);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<CodeBarresDto>> codes(Long idArticle) {
        return ResponseEntity.ok(conditionnementService.codes(idArticle));
    }

    @Override
    public ResponseEntity<CodeBarresDto> ajouterCode(Long idArticle, CodeBarresDto dto) {
        return ResponseEntity.ok(conditionnementService.ajouterCode(idArticle, dto));
    }

    @Override
    public ResponseEntity<CodeBarresDto> genererCodeInterne(Long idArticle, Long idConditionnement) {
        return ResponseEntity.ok(conditionnementService.genererCodeInterne(idArticle, idConditionnement));
    }

    @Override
    public ResponseEntity<Void> retirerCode(Long idArticle, Long idCode) {
        conditionnementService.retirerCode(idArticle, idCode);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ResultatScanDto> scanner(String code) {
        return ResponseEntity.ok(conditionnementService.scanner(code));
    }
}
