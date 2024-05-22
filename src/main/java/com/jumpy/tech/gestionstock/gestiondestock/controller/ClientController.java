package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.ClientApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.service.ClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
@RestController
public class ClientController implements ClientApi {
    private ClientService clientService;

    public ClientController(ClientService clientService){
        this.clientService=clientService;
    }

    @Override
    public ResponseEntity<ClientDto> save(ClientDto dto) {
        return ResponseEntity.status(HttpStatus.OK).body(clientService.save(dto));
    }

    @Override
    public ResponseEntity<ClientDto> findById(Long idClient) {
        return ResponseEntity.status(HttpStatus.OK).body(clientService.findById(idClient));
    }

    @Override
    public ResponseEntity<List<ClientDto>> findAll() {
        return ResponseEntity.status(HttpStatus.OK).body(clientService.findAll());
    }

    @Override
    public ResponseEntity delete(Long idClient) {
        clientService.delete(idClient);
        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
