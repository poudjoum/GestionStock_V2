package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.dto.PolitiqueFideliteDto;
import com.jumpy.tech.gestionstock.gestiondestock.fidelite.PolitiqueFidelite;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

@RestController
@RequestMapping(value = APP_ROOT + "/fidelite/politique", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Politique de fidélité", description = "Ce que rapporte un achat et ce que valent les points, réglés par le gérant")
public class PolitiqueFideliteController {

    private final PolitiqueFidelite politique;

    public PolitiqueFideliteController(PolitiqueFidelite politique) {
        this.politique = politique;
    }

    @GetMapping
    @Operation(summary = "La politique de fidélité de mon magasin")
    public ResponseEntity<PolitiqueFideliteDto> lire() {
        return ResponseEntity.ok(politique.lire());
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Régler la politique de fidélité de mon magasin",
            description = "Un champ absent reste inchangé. Vaut pour les prochains tickets et les prochains bons.")
    public ResponseEntity<PolitiqueFideliteDto> regler(@RequestBody PolitiqueFideliteDto dto) {
        return ResponseEntity.ok(politique.regler(dto));
    }
}
