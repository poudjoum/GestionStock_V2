package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.MesSitesDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.SeuilDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.SiteDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

@Tag(name = "Sites", description = "Magasins et entrepots de l'entreprise")
public interface SiteApi {

    @GetMapping(value = APP_ROOT + "/sites", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<SiteDto>> sites();

    /** Les sites ou le compte travaille : de quoi dessiner le selecteur de site. */
    @GetMapping(value = APP_ROOT + "/sites/miens", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<MesSitesDto> mesSites();

    @PostMapping(value = APP_ROOT + "/sites", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<SiteDto> creer(@RequestBody SiteDto dto);

    @PutMapping(value = APP_ROOT + "/sites/{idSite}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<SiteDto> modifier(@PathVariable("idSite") Long idSite, @RequestBody SiteDto dto);

    @PostMapping(value = APP_ROOT + "/sites/{idSite}/fermeture", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<SiteDto> fermer(@PathVariable("idSite") Long idSite);

    /** Le seuil d'alerte d'un article dans un site. `{"seuil": null}` rend la main au seuil de l'article. */
    @PutMapping(value = APP_ROOT + "/articles/{idArticle}/seuils/{idSite}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> definirSeuil(@PathVariable("idArticle") Long idArticle,
                                      @PathVariable("idSite") Long idSite, @RequestBody SeuilDto seuil);
}
