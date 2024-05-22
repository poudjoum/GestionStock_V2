package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.CommandFourApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeFourDto;
import com.jumpy.tech.gestionstock.gestiondestock.service.CommandeFourService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
@RestController
public class CommandeFourController implements CommandFourApi {

    private CommandeFourService cmndeFourServ;

    public CommandeFourController(CommandeFourService cmndeFourServ) {
        this.cmndeFourServ = cmndeFourServ;
    }


    @Override
    public ResponseEntity<CommandeFourDto> save(CommandeFourDto dto) {
        return ResponseEntity.status(HttpStatus.OK).body(cmndeFourServ.save(dto));
    }

    @Override
    public ResponseEntity<CommandeFourDto> findById(Long idCommandFour) {
        return ResponseEntity.status(HttpStatus.OK).body(cmndeFourServ.findById(idCommandFour));
    }

    @Override
    public ResponseEntity<CommandeFourDto> findByCode(String code) {
        return ResponseEntity.status(HttpStatus.OK).body(cmndeFourServ.findByCode(code));
    }

    @Override
    public ResponseEntity<List<CommandeFourDto>> findAll() {
        return ResponseEntity.status(HttpStatus.OK).body(cmndeFourServ.findAll());
    }

    @Override
    public ResponseEntity delete(Long idCommandFour) {
        cmndeFourServ.delete(idCommandFour);
        return ResponseEntity.ok().build();
    }
}
