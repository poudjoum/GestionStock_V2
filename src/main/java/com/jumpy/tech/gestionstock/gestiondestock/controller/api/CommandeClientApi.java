package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.ClotureDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCommandeClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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

    /**
     * Solde le reliquat d'une commande partiellement servie : `{ "motif": "client desiste" }`.
     *
     * Rien ne sort du magasin : ce qui n'a pas ete vendu n'a jamais quitte le stock.
     */
    @PostMapping(value = APP_ROOT+"/commandes-clients/{idCommandClient}/cloture",consumes = MediaType.APPLICATION_JSON_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CommandeClientDto> cloturer(@PathVariable Long idCommandClient,
                                               @RequestBody ClotureDto cloture);

    // --- Lignes de la commande ------------------------------------------------------------

    @GetMapping(value = APP_ROOT+"/commandes-clients/{idCommandClient}/lignes",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<LigneCommandeClientDto>> lignes(@PathVariable Long idCommandClient);

    @PostMapping(value = APP_ROOT+"/commandes-clients/{idCommandClient}/lignes",consumes = MediaType.APPLICATION_JSON_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<LigneCommandeClientDto> ajouterLigne(@PathVariable Long idCommandClient,
                                                        @RequestBody LigneCommandeClientDto ligne);

    @PatchMapping(value = APP_ROOT+"/commandes-clients/{idCommandClient}/lignes/{idLigne}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<LigneCommandeClientDto> modifierQuantite(@PathVariable Long idCommandClient,
                                                            @PathVariable Long idLigne,
                                                            @RequestParam BigDecimal quantite);

    @DeleteMapping(value = APP_ROOT+"/commandes-clients/{idCommandClient}/lignes/{idLigne}")
    ResponseEntity<Void> retirerLigne(@PathVariable Long idCommandClient, @PathVariable Long idLigne);

    @DeleteMapping(value = APP_ROOT+"/commandes-clients/delete/{idCommandClient}")
    ResponseEntity delete(@PathVariable Long idCommandClient);
}
