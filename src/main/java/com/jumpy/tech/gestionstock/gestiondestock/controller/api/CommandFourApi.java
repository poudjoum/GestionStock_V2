package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeFourDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

public interface CommandFourApi {
    @Tag(name="Post",description = "Post Methods of Gestion de Stock APIs")
    @PostMapping(value = APP_ROOT+"/command-four/create",consumes = MediaType.APPLICATION_JSON_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CommandeFourDto> save(CommandeFourDto dto);
    @Tag(name="Get",description = "Get Methods of Gestion de Stock APIs")
    @GetMapping(value = APP_ROOT+"/commande-four/{idCommandFour}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CommandeFourDto> findById(@PathVariable Long idCommandFour);
    @GetMapping(value = APP_ROOT+"/command-four/{code}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CommandeFourDto> findByCode(@PathVariable String code);
    @GetMapping(value = APP_ROOT+"/command-four/all",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List< CommandeFourDto>> findAll();
    @DeleteMapping(value = APP_ROOT+"/command-client/delete/{idCommandFour}")
    ResponseEntity delete(@PathVariable Long idCommandFour);
}
