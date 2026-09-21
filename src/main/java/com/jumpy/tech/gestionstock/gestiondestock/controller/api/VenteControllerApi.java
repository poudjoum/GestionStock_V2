package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

public interface VenteControllerApi {

    @PostMapping(path = APP_ROOT+"/ventes/create")
    ResponseEntity<VenteDto> save(@RequestBody VenteDto dto);

    @GetMapping(path = APP_ROOT+"/ventes/{id}")
    ResponseEntity<VenteDto> findById(@PathVariable Long id);

    @GetMapping(path = APP_ROOT+"/ventes/all")
    ResponseEntity<List<VenteDto>> findAll();

    /** Liste paginee : `?page=0&size=20&sort=code,asc`. */
    @GetMapping(path = APP_ROOT+"/ventes")
    ResponseEntity<Page<VenteDto>> findAll(Pageable pageable);

    // Deux corrections ici. Le chemin partageait le motif de findById — `/ventes/{id}` et
    // `/ventes/{codeVente}` sont indiscernables — et le parametre n'etait pas annote : sans
    // @PathVariable, Spring n'y injectait rien et la recherche partait avec un code vide.
    @GetMapping(path = APP_ROOT+"/ventes/code/{codeVente}")
    ResponseEntity<VenteDto> findVenteByCode(@PathVariable("codeVente") String codeVente);

    // --- Correction d'une vente -------------------------------------------------------------
    // Contrairement a une commande, une vente a deja sorti sa marchandise : chacune de ces
    // operations ecrit un mouvement de compensation, et ne se contente pas de reecrire la ligne.

    @GetMapping(path = APP_ROOT+"/ventes/{idVente}/lignes")
    ResponseEntity<List<LigneVenteDto>> lignes(@PathVariable Long idVente);

    /**
     * Ajoute un article a une vente en cours : le geste du comptoir, ou le panier se construit
     * article par article. La sortie de stock est immediate.
     */
    @PostMapping(path = APP_ROOT+"/ventes/{idVente}/lignes", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<LigneVenteDto> ajouterLigne(@PathVariable Long idVente,
                                               @RequestBody LigneVenteDto ligne);

    /**
     * Cree la vente qui sert une commande client validee, et passe la commande en livree. C'est
     * le seul chemin par lequel une vente connait son client.
     */
    @PostMapping(path = APP_ROOT+"/commandes-clients/{idCommandeClient}/vente")
    ResponseEntity<VenteDto> servirCommandeClient(@PathVariable Long idCommandeClient);

    /** Annule la vente et remet sa marchandise en magasin. La vente reste lisible. */
    @PostMapping(path = APP_ROOT+"/ventes/{idVente}/annulation")
    ResponseEntity<VenteDto> annuler(@PathVariable Long idVente);

    @PatchMapping(path = APP_ROOT+"/ventes/{idVente}/lignes/{idLigne}")
    ResponseEntity<LigneVenteDto> modifierQuantite(@PathVariable Long idVente,
                                                   @PathVariable Long idLigne,
                                                   @RequestParam BigDecimal quantite);

    @DeleteMapping(path = APP_ROOT+"/ventes/{idVente}/lignes/{idLigne}")
    ResponseEntity<Void> retirerLigne(@PathVariable Long idVente, @PathVariable Long idLigne);

    // La barre oblique manquait : le chemin valait « gestiondestock/v1ventes/delete/{id} », que
    // personne ne pouvait appeler.
    @DeleteMapping(path = APP_ROOT+"/ventes/delete/{id}")
    ResponseEntity delete(@PathVariable Long id);
}
