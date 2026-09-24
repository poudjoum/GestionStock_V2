package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.InventaireApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ComptageDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneComptageDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.OuvertureInventaireDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.SeanceInventaireDto;
import com.jumpy.tech.gestionstock.gestiondestock.service.InventaireService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InventaireController implements InventaireApi {

    private final InventaireService inventaireService;

    public InventaireController(InventaireService inventaireService) {
        this.inventaireService = inventaireService;
    }

    @Override
    public ResponseEntity<SeanceInventaireDto> ouvrir(OuvertureInventaireDto ouverture) {
        String commentaire = ouverture == null ? null : ouverture.commentaire();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventaireService.ouvrir(commentaire));
    }

    @Override
    public ResponseEntity<SeanceInventaireDto> seanceOuverte() {
        SeanceInventaireDto seance = inventaireService.seanceOuverte();
        // 204 et non 404 : n'avoir aucun inventaire en cours est l'etat ordinaire d'un magasin.
        return seance == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(seance);
    }

    @Override
    public ResponseEntity<Page<SeanceInventaireDto>> historique(Pageable pageable) {
        return ResponseEntity.ok(inventaireService.historique(pageable));
    }

    @Override
    public ResponseEntity<SeanceInventaireDto> seance(Long idSeance) {
        return ResponseEntity.ok(inventaireService.seance(idSeance));
    }

    @Override
    public ResponseEntity<Page<LigneComptageDto>> lignes(Long idSeance, String q, String vue,
                                                         Pageable pageable) {
        return ResponseEntity.ok(inventaireService.lignes(idSeance, q, vue, pageable));
    }

    @Override
    public ResponseEntity<LigneComptageDto> compter(Long idSeance, ComptageDto comptage) {
        // Le code l'emporte sur l'identifiant : c'est celui qu'on vient de lire sur l'article
        // qu'on tient en main, tandis que l'identifiant vient d'une liste affichee plus tot.
        LigneComptageDto ligne = StringUtils.hasText(comptage.codeArticle())
                ? inventaireService.compterParCode(idSeance, comptage.codeArticle(), comptage.quantite())
                : inventaireService.compter(idSeance, comptage.idArticle(), comptage.quantite());
        return ResponseEntity.ok(ligne);
    }

    @Override
    public ResponseEntity<LigneComptageDto> annulerComptage(Long idSeance, Long idLigne) {
        return ResponseEntity.ok(inventaireService.annulerComptage(idSeance, idLigne));
    }

    @Override
    public ResponseEntity<SeanceInventaireDto> valider(Long idSeance) {
        return ResponseEntity.ok(inventaireService.valider(idSeance));
    }

    @Override
    public ResponseEntity<SeanceInventaireDto> abandonner(Long idSeance) {
        return ResponseEntity.ok(inventaireService.abandonner(idSeance));
    }
}
