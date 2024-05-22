package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

public interface EntrepriseControllerApi {
    @Tag(name="Post",description = "Post Methods of Gestion de Stock APIs")
    @PostMapping(value = APP_ROOT+"/entreprise/create",consumes = MediaType.APPLICATION_JSON_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<EntrepriseDto> save(@RequestBody EntrepriseDto dto);
    @Tag(name="Get",description = "Get Methods of Gestion de Stock APIs")
    @GetMapping(value = APP_ROOT+"/entreprise/{idEntreprise}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<EntrepriseDto> findById(@PathVariable Long idEntreprise);
    @GetMapping(value = APP_ROOT+"/entreprises/all",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<EntrepriseDto>> findAll();
    @DeleteMapping(value = APP_ROOT+"/entreprise/delete/{idEntreprise}")
    ResponseEntity delete(@PathVariable Long idEntreprise);
}
