package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeClientDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

public interface CommandeClientApi {
    @Tag(name="Post",description = "Post Methods of Gestion de Stock APIs")
    @PostMapping(value = APP_ROOT+"/command-client/create",consumes = MediaType.APPLICATION_JSON_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CommandeClientDto> save(@RequestBody CommandeClientDto dto);
    @Tag(name="Get",description = "Get Methods of Gestion de Stock APIs")
    @GetMapping(value = APP_ROOT+"/commande-client/{idCommandClient}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CommandeClientDto> findById(@PathVariable Long idCommandClient);
    @GetMapping(value = APP_ROOT+"/command-client/{code}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CommandeClientDto> findByCode(String code);
    @GetMapping(value = APP_ROOT+"/command-client/all",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<CommandeClientDto>>findAll();
    @DeleteMapping(value = APP_ROOT+"/command-client/delete/{idCommandClient}")
    ResponseEntity delete(@PathVariable Long idCommandClient);
}
