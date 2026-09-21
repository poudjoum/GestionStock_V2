package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.ClotureDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeFourDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCmndeFournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneReceptionDto;
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
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
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
     * Liste paginee et filtrable : `?etat=VALIDEE&etat=PARTIELLEMENT_LIVREE&q=cim&page=0&size=20`.
     *
     * Le filtre par etat est ce dont le quai a besoin : le magasinier qui decharge un camion
     * cherche les commandes qu'il peut recevoir, pas l'historique des achats de la maison.
     * `q` porte sur le code de la commande et le nom du fournisseur — ce qui figure sur le bon de
     * livraison qu'il a en main.
     */
    @GetMapping(value = APP_ROOT+"/commandes-fournisseurs",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<org.springframework.data.domain.Page<CommandeFourDto>> rechercher(
            @RequestParam(required = false) List<EtatCommande> etat,
            @RequestParam(required = false) String q,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Fait avancer la commande. `PATCH` et non `PUT` : on ne remplace pas la commande, on change
     * l'un de ses champs. Le passage a LIVREE fait entrer la marchandise en magasin.
     */
    @PatchMapping(value = APP_ROOT+"/commandes-fournisseurs/{idCommandFour}/etat/{etat}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CommandeFourDto> mettreAJourEtat(@PathVariable Long idCommandFour,
                                                    @PathVariable EtatCommande etat);

    /**
     * Enregistre ce qui est reellement arrive : `[{ "idLigne": 12, "quantite": 6 }]`.
     *
     * La quantite est celle de cette arrivee, pas le cumul. L'etat de la commande n'est pas a
     * declarer — il se deduit de ce qui reste attendu.
     */
    @PostMapping(value = APP_ROOT+"/commandes-fournisseurs/{idCommandFour}/receptions",consumes = MediaType.APPLICATION_JSON_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CommandeFourDto> recevoir(@PathVariable Long idCommandFour,
                                             @RequestBody List<LigneReceptionDto> receptions);

    /**
     * Solde le reliquat d'une commande partiellement livree : `{ "motif": "fournisseur en rupture" }`.
     *
     * `POST` et non `PATCH .../etat/CLOTUREE` : clore n'est pas declarer un etat au hasard, c'est
     * un geste qui porte une explication, et l'etat en decoule. Rien n'entre en stock.
     */
    @PostMapping(value = APP_ROOT+"/commandes-fournisseurs/{idCommandFour}/cloture",consumes = MediaType.APPLICATION_JSON_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CommandeFourDto> cloturer(@PathVariable Long idCommandFour,
                                             @RequestBody ClotureDto cloture);

    // --- Lignes de la commande ------------------------------------------------------------
    // Une commande enregistree ne se corrigeait pas : ni ajout, ni retrait, ni changement de
    // quantite. Ces operations restent ouvertes tant que la commande n'est pas figee.

    @GetMapping(value = APP_ROOT+"/commandes-fournisseurs/{idCommandFour}/lignes",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<LigneCmndeFournisseurDto>> lignes(@PathVariable Long idCommandFour);

    @PostMapping(value = APP_ROOT+"/commandes-fournisseurs/{idCommandFour}/lignes",consumes = MediaType.APPLICATION_JSON_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<LigneCmndeFournisseurDto> ajouterLigne(@PathVariable Long idCommandFour,
                                                          @RequestBody LigneCmndeFournisseurDto ligne);

    @PatchMapping(value = APP_ROOT+"/commandes-fournisseurs/{idCommandFour}/lignes/{idLigne}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<LigneCmndeFournisseurDto> modifierQuantite(@PathVariable Long idCommandFour,
                                                              @PathVariable Long idLigne,
                                                              @RequestParam BigDecimal quantite);

    @DeleteMapping(value = APP_ROOT+"/commandes-fournisseurs/{idCommandFour}/lignes/{idLigne}")
    ResponseEntity<Void> retirerLigne(@PathVariable Long idCommandFour, @PathVariable Long idLigne);

    @DeleteMapping(value = APP_ROOT+"/commandes-fournisseurs/delete/{idCommandFour}")
    ResponseEntity delete(@PathVariable Long idCommandFour);
}
