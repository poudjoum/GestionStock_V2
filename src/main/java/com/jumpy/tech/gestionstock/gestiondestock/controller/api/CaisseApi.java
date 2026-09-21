package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.EtatDeCaisseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ReglementDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

public interface CaisseApi {

    /**
     * Ce qui est entre en caisse, par moyen de paiement.
     *
     * Sans dates, la journee en cours — c'est la question du soir, quand on ferme. Les deux bornes
     * sont des jours civils locaux, pas des instants : `?debut=2026-09-01&fin=2026-09-30`.
     */
    @Tag(name = "Get", description = "Get Methods of Gestion de Stock APIs")
    @GetMapping(value = APP_ROOT + "/caisse/etat", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<EtatDeCaisseDto> etat(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin);

    /** Le detail des encaissements de la periode, pour confronter le total a ce qui le compose. */
    @GetMapping(value = APP_ROOT + "/caisse/reglements", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Page<ReglementDto>> reglements(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            Pageable pageable);
}
