package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.analyse.AnalyseArticlesDto;
import com.jumpy.tech.gestionstock.gestiondestock.analyse.AnalyseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

@RestController
public class AnalyseController {

    private final AnalyseService analyseService;

    public AnalyseController(AnalyseService analyseService) {
        this.analyseService = analyseService;
    }

    /** Classes ABC, couverture et dormants sur les `jours` derniers jours. */
    @Tag(name = "Get", description = "Get Methods of Gestion de Stock APIs")
    @GetMapping(value = APP_ROOT + "/analyses/articles", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AnalyseArticlesDto> articles(@RequestParam(defaultValue = "90") int jours,
                                                       @RequestParam(defaultValue = "false") boolean tousSites) {
        return ResponseEntity.ok(analyseService.articles(jours, tousSites));
    }
}
