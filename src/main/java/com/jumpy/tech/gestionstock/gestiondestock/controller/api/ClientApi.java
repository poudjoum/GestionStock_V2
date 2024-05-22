package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.ClientDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

public interface ClientApi {
    @Tag(name="Post",description = "Post Methods of Gestion de Stock APIs")
    @PostMapping(value = APP_ROOT+"/clients/create",consumes = MediaType.APPLICATION_JSON_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ClientDto> save(@RequestBody ClientDto dto);
    @Tag(name="Get",description = "Get Methods of Gestion de Stock APIs")
    @GetMapping(value = APP_ROOT+"/clients/{idClient}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ClientDto> findById(@PathVariable Long idClient);
    @GetMapping(value = APP_ROOT+"/clients/all",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<ClientDto>> findAll();
    @DeleteMapping(value = APP_ROOT+"/clients/delete/{idClient}")
    ResponseEntity delete(@PathVariable Long idClient);
}
