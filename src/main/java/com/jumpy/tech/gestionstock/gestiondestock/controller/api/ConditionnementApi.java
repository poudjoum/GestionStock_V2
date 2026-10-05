package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.CodeBarresDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ConditionnementDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ResultatScanDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

@Tag(name = "Conditionnements", description = "Conditionnements et codes-barres des articles")
public interface ConditionnementApi {

    @GetMapping(value = APP_ROOT + "/articles/{idArticle}/conditionnements", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<ConditionnementDto>> conditionnements(@PathVariable("idArticle") Long idArticle);

    @PostMapping(value = APP_ROOT + "/articles/{idArticle}/conditionnements",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ConditionnementDto> ajouter(@PathVariable("idArticle") Long idArticle,
                                               @RequestBody ConditionnementDto dto);

    @PutMapping(value = APP_ROOT + "/articles/{idArticle}/conditionnements/{idConditionnement}",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ConditionnementDto> modifier(@PathVariable("idArticle") Long idArticle,
                                                @PathVariable("idConditionnement") Long idConditionnement,
                                                @RequestBody ConditionnementDto dto);

    @DeleteMapping(value = APP_ROOT + "/articles/{idArticle}/conditionnements/{idConditionnement}")
    ResponseEntity<Void> retirer(@PathVariable("idArticle") Long idArticle,
                                 @PathVariable("idConditionnement") Long idConditionnement);

    @GetMapping(value = APP_ROOT + "/articles/{idArticle}/codes-barres", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<CodeBarresDto>> codes(@PathVariable("idArticle") Long idArticle);

    @PostMapping(value = APP_ROOT + "/articles/{idArticle}/codes-barres",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CodeBarresDto> ajouterCode(@PathVariable("idArticle") Long idArticle,
                                              @RequestBody CodeBarresDto dto);

    /** `?idConditionnement=` facultatif : sans lui, le code designe l'unite de base. */
    @PostMapping(value = APP_ROOT + "/articles/{idArticle}/codes-barres/interne",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CodeBarresDto> genererCodeInterne(@PathVariable("idArticle") Long idArticle,
                                                     @RequestParam(required = false) Long idConditionnement);

    @DeleteMapping(value = APP_ROOT + "/articles/{idArticle}/codes-barres/{idCode}")
    ResponseEntity<Void> retirerCode(@PathVariable("idArticle") Long idArticle,
                                     @PathVariable("idCode") Long idCode);

    /**
     * Ce que designe un code lu : `?code=3017620422003`. En parametre et non dans le chemin, parce
     * qu'un QR code peut porter des barres obliques.
     */
    @GetMapping(value = APP_ROOT + "/articles/scan", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ResultatScanDto> scanner(@RequestParam("code") String code);
}
