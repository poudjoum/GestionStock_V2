package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeFourDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

/**
 * Les chemins etaient repartis entre trois prefixes — `/command-four`, `/commande-four` et, pour
 * la suppression, `/command-client`. Ce dernier n'etait pas qu'inelegant : il reprenait mot pour
 * mot le chemin de suppression des commandes client, et les deux routes devenaient indiscernables
 * a l'appel. Tout est desormais sous `/commandes-fournisseurs`.
 */
public interface CommandFourApi {

    @Tag(name="Post",description = "Post Methods of Gestion de Stock APIs")
    @PostMapping(value = APP_ROOT+"/commandes-fournisseurs/create",consumes = MediaType.APPLICATION_JSON_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)
    // @RequestBody manquait : le corps n'etait pas lie.
    ResponseEntity<CommandeFourDto> save(@RequestBody CommandeFourDto dto);

    @Tag(name="Get",description = "Get Methods of Gestion de Stock APIs")
    @GetMapping(value = APP_ROOT+"/commandes-fournisseurs/{idCommandFour}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CommandeFourDto> findById(@PathVariable Long idCommandFour);

    @GetMapping(value = APP_ROOT+"/commandes-fournisseurs/code/{code}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CommandeFourDto> findByCode(@PathVariable String code);

    @GetMapping(value = APP_ROOT+"/commandes-fournisseurs/all",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<CommandeFourDto>> findAll();

    /**
     * Fait avancer la commande. `PATCH` et non `PUT` : on ne remplace pas la commande, on change
     * l'un de ses champs. Le passage a LIVREE fait entrer la marchandise en magasin.
     */
    @PatchMapping(value = APP_ROOT+"/commandes-fournisseurs/{idCommandFour}/etat/{etat}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CommandeFourDto> mettreAJourEtat(@PathVariable Long idCommandFour,
                                                    @PathVariable EtatCommande etat);

    @DeleteMapping(value = APP_ROOT+"/commandes-fournisseurs/delete/{idCommandFour}")
    ResponseEntity delete(@PathVariable Long idCommandFour);
}
