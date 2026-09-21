package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    // La barre oblique manquait : le chemin valait « gestiondestock/v1ventes/delete/{id} », que
    // personne ne pouvait appeler.
    @DeleteMapping(path = APP_ROOT+"/ventes/delete/{id}")
    ResponseEntity delete(@PathVariable Long id);
}
