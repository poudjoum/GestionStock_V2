package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

/**
 * Chemins unifies sous `/commandes-clients` : ils etaient repartis entre `/command-client` et
 * `/commande-client`, et la recherche par code partageait le motif de la recherche par
 * identifiant.
 */
public interface CommandeClientApi {

    @Tag(name="Post",description = "Post Methods of Gestion de Stock APIs")
    @PostMapping(value = APP_ROOT+"/commandes-clients/create",consumes = MediaType.APPLICATION_JSON_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CommandeClientDto> save(@RequestBody CommandeClientDto dto);

    @Tag(name="Get",description = "Get Methods of Gestion de Stock APIs")
    @GetMapping(value = APP_ROOT+"/commandes-clients/{idCommandClient}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CommandeClientDto> findById(@PathVariable Long idCommandClient);

    // Le parametre n'etait pas annote : sans @PathVariable, la recherche partait avec un code vide.
    @GetMapping(value = APP_ROOT+"/commandes-clients/code/{code}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CommandeClientDto> findByCode(@PathVariable("code") String code);

    @GetMapping(value = APP_ROOT+"/commandes-clients/all",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<CommandeClientDto>>findAll();

    /** Fait avancer la commande. Aucune transition ne touche au stock : c'est la vente qui sort. */
    @PatchMapping(value = APP_ROOT+"/commandes-clients/{idCommandClient}/etat/{etat}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CommandeClientDto> mettreAJourEtat(@PathVariable Long idCommandClient,
                                                      @PathVariable EtatCommande etat);

    @DeleteMapping(value = APP_ROOT+"/commandes-clients/delete/{idCommandClient}")
    ResponseEntity delete(@PathVariable Long idCommandClient);
}
