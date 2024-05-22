package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.FournisseurDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

public interface FournisseurControllerApi {
    @Tag(name="Post",description = "Post Methods of Gestion de Stock APIs")
    @PostMapping(value = APP_ROOT+"/fournisseur/create",consumes = MediaType.APPLICATION_JSON_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<FournisseurDto> save(FournisseurDto dto);
    @Tag(name="Get",description = "Get Methods of Gestion de Stock APIs")
    @GetMapping(value = APP_ROOT+"/fournisseur/{idFour}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<FournisseurDto> findById(@PathVariable Long idFour);
    @GetMapping(value = APP_ROOT+"/fournisseur/{nomFournisseur}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<FournisseurDto> findFournisseurByNom(@PathVariable String nomFournisseur);
    @GetMapping(value = APP_ROOT+"/fournisseur/all",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<FournisseurDto>> findAll();
    @DeleteMapping(value = APP_ROOT+"/fournisseur/delete/{idFour}")
    ResponseEntity<FournisseurDto> delete(@PathVariable Long idFour);
}
