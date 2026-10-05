package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.LotDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.RappelLotDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

public interface LotApi {

    /** Les lots en stock d'un article, premier perime en tete : ce qu'on choisit au comptoir. */
    @Tag(name = "Get", description = "Get Methods of Gestion de Stock APIs")
    @GetMapping(value = APP_ROOT + "/lots/article/{idArticle}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<LotDto>> lotsDeLArticle(@PathVariable("idArticle") Long idArticle);

    /** Ce qui perime bientot ou a perime, et reste en rayon. */
    @GetMapping(value = APP_ROOT + "/lots/peremption", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<LotDto>> peremption(@RequestParam(defaultValue = "false") boolean tousSites);

    /** Rappel d'un lot : ou il en reste, a qui il a ete vendu. */
    @GetMapping(value = APP_ROOT + "/lots/{idLot}/rappel", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<RappelLotDto> rappel(@PathVariable("idLot") Long idLot);
}
