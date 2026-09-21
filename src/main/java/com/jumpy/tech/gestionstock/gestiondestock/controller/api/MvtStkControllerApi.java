package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.List;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

public interface MvtStkControllerApi {

    @Tag(name = "Get", description = "Get Methods of Gestion de Stock APIs")
    @GetMapping(value = APP_ROOT + "/mouvements/stockreel/{idArticle}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<BigDecimal> stockReelArticle(@PathVariable("idArticle") Long idArticle);

    @GetMapping(value = APP_ROOT + "/mouvements/article/{idArticle}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<MvtStkDto>> mvtStkArticle(@PathVariable("idArticle") Long idArticle);

    // Le sens du mouvement est porte par le chemin et non par le corps : c'est ce qui empeche une
    // sortie d'arriver marquee « entree » et d'augmenter le stock qu'elle devait diminuer.
    @Tag(name = "Post", description = "Post Methods of Gestion de Stock APIs")
    @PostMapping(value = APP_ROOT + "/mouvements/entree", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<MvtStkDto> entreeStock(@RequestBody MvtStkDto dto);

    @PostMapping(value = APP_ROOT + "/mouvements/sortie", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<MvtStkDto> sortieStock(@RequestBody MvtStkDto dto);
}
