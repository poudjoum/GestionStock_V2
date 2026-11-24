package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.demarrage.Demarrage;
import com.jumpy.tech.gestionstock.gestiondestock.demarrage.DemarrageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

@RestController
@RequestMapping(value = APP_ROOT + "/entreprises/mienne/demarrage", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Démarrage", description = "Les premiers pas d'un commerce qu'on vient d'inscrire")
public class DemarrageController {

    private final Demarrage demarrage;

    public DemarrageController(Demarrage demarrage) {
        this.demarrage = demarrage;
    }

    @GetMapping
    @Operation(summary = "Où en est mon commerce de ses premiers pas",
            description = "Chaque étape se déduit des données du commerce : rien ne se coche à la main.")
    public ResponseEntity<DemarrageDto> etat() {
        return ResponseEntity.ok(demarrage.etat());
    }

    @PutMapping("/masque")
    @Operation(summary = "Fermer l'assistant de démarrage, ou le rouvrir",
            description = "Fermé, il ne s'affiche plus sur l'accueil ; il reste joignable depuis les réglages.")
    public ResponseEntity<DemarrageDto> masquer(@RequestParam boolean masque) {
        return ResponseEntity.ok(demarrage.masquer(masque));
    }
}
