package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
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
    @GetMapping(path = APP_ROOT+"/ventes/{codeVente}")
    ResponseEntity<VenteDto>findVenteByCode(String codeVente);
    @DeleteMapping(path = APP_ROOT+"ventes/delete/{id}")
    ResponseEntity delete(@PathVariable Long id);
}
