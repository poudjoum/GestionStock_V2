package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.dto.CampagneDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CampagnePubliqueDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.PromotionsDuJourDto;
import com.jumpy.tech.gestionstock.gestiondestock.promotion.Campagnes;
import com.jumpy.tech.gestionstock.gestiondestock.promotion.CampagnesPubliques;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

@RestController
@Tag(name = "Campagnes de promotion", description = "Les promotions du magasin : préparées par le gérant, appliquées en caisse, montrées dans l'application")
public class CampagneController {

    private final Campagnes campagnes;
    private final CampagnesPubliques vitrine;

    public CampagneController(Campagnes campagnes, CampagnesPubliques vitrine) {
        this.campagnes = campagnes;
        this.vitrine = vitrine;
    }

    @GetMapping(value = APP_ROOT + "/campagnes", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Les campagnes de mon magasin, des plus récentes aux plus anciennes")
    public ResponseEntity<List<CampagneDto>> lister() {
        return ResponseEntity.ok(campagnes.lister());
    }

    /** Declaree avant `/campagnes/{id}`, qui sinon prendrait le mot pour un identifiant. */
    @GetMapping(value = APP_ROOT + "/campagnes/promotions-en-cours", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Les articles en promotion aujourd'hui", description = "Ce que la caisse garde pour vendre au bon prix, même hors ligne")
    public ResponseEntity<PromotionsDuJourDto> promotionsEnCours() {
        return ResponseEntity.ok(campagnes.promotionsEnCours());
    }

    @GetMapping(value = APP_ROOT + "/campagnes/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CampagneDto> lire(@PathVariable Long id) {
        return ResponseEntity.ok(campagnes.lire(id));
    }

    @PostMapping(value = APP_ROOT + "/campagnes", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Préparer une campagne")
    public ResponseEntity<CampagneDto> creer(@RequestBody CampagneDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campagnes.creer(dto));
    }

    @PutMapping(value = APP_ROOT + "/campagnes/{id}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Corriger une campagne pas encore finie")
    public ResponseEntity<CampagneDto> modifier(@PathVariable Long id, @RequestBody CampagneDto dto) {
        return ResponseEntity.ok(campagnes.modifier(id, dto));
    }

    @PostMapping(value = APP_ROOT + "/campagnes/{id}/arret", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Arrêter une campagne avant son terme", description = "Les prix normaux reprennent aussitôt")
    public ResponseEntity<CampagneDto> arreter(@PathVariable Long id) {
        return ResponseEntity.ok(campagnes.arreter(id));
    }

    @GetMapping(value = APP_ROOT + "/fidelite/campagnes", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Les campagnes en cours, tous magasins", description = "La vitrine de l'application mobile, lisible sans compte")
    public ResponseEntity<List<CampagnePubliqueDto>> vitrine() {
        return ResponseEntity.ok(vitrine.enCours());
    }
}
