package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.FactureDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ReglementDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

public interface FactureApi {

    /**
     * Emet la facture d'une vente. `POST` sur la vente et non sur `/factures` : c'est la vente
     * qui commande, et une facture ne s'invente pas hors d'elle.
     */
    @Tag(name = "Post", description = "Post Methods of Gestion de Stock APIs")
    @PostMapping(value = APP_ROOT + "/ventes/{idVente}/facture", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<FactureDto> emettre(@PathVariable Long idVente);

    @Tag(name = "Get", description = "Get Methods of Gestion de Stock APIs")
    @GetMapping(value = APP_ROOT + "/ventes/{idVente}/facture", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<FactureDto> factureDeLaVente(@PathVariable Long idVente);

    @GetMapping(value = APP_ROOT + "/factures/{idFacture}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<FactureDto> findById(@PathVariable Long idFacture);

    @GetMapping(value = APP_ROOT + "/factures/numero/{numero}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<FactureDto> findByNumero(@PathVariable String numero);

    /** Liste paginee : `?page=0&size=20&sort=dateEmission,desc`. */
    @GetMapping(value = APP_ROOT + "/factures", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Page<FactureDto>> findAll(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String q,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String statut,
            Pageable pageable);

    /** Annule la facture sans la supprimer, et rouvre la vente a la correction. */
    @PostMapping(value = APP_ROOT + "/factures/{idFacture}/annulation", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<FactureDto> annuler(@PathVariable Long idFacture);

    // --- Reglement ---------------------------------------------------------------------------
    // Une facture peut etre reglee en plusieurs fois : un acompte puis le solde est le cas
    // ordinaire. Ce qui reste du se deduit de la somme des reglements, a chaque lecture.

    @PostMapping(value = APP_ROOT + "/factures/{idFacture}/reglements", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ReglementDto> regler(@PathVariable Long idFacture, @RequestBody ReglementDto reglement);

    @GetMapping(value = APP_ROOT + "/factures/{idFacture}/reglements", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<ReglementDto>> reglements(@PathVariable Long idFacture);

    /** Efface un encaissement saisi par erreur : un reglement ne se modifie pas, il se reprend. */
    @DeleteMapping(value = APP_ROOT + "/factures/{idFacture}/reglements/{idReglement}")
    ResponseEntity<Void> supprimerReglement(@PathVariable Long idFacture, @PathVariable Long idReglement);
}
