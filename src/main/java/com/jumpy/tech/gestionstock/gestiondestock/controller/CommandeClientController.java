package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.CommandeClientApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.service.CommandeClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
@RestController
public class CommandeClientController implements CommandeClientApi {

    private CommandeClientService cmdeCliService;
    public CommandeClientController(CommandeClientService clientService){
        this.cmdeCliService=clientService;
    }

    @Override
    public ResponseEntity<CommandeClientDto> save(CommandeClientDto dto) {
        return ResponseEntity.status(HttpStatus.OK).body(cmdeCliService.save(dto));
    }

    @Override
    public ResponseEntity<CommandeClientDto> findById(Long idCommandClient) {
        return ResponseEntity.status(HttpStatus.OK).body(cmdeCliService.findById(idCommandClient));
    }

    @Override
    public ResponseEntity<CommandeClientDto> findByCode(String code) {
        return ResponseEntity.status(HttpStatus.OK).body(cmdeCliService.findByCode(code));
    }

    @Override
    public ResponseEntity<List<CommandeClientDto>> findAll() {
        return ResponseEntity.status(HttpStatus.OK).body(cmdeCliService.findAll());
    }

    @Override
    public ResponseEntity delete(Long idCommandClient) {
        cmdeCliService.delete(idCommandClient);
        return ResponseEntity.ok().build();
    }
}
