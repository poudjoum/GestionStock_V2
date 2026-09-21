package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.CommandFourApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeFourDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCmndeFournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;
import com.jumpy.tech.gestionstock.gestiondestock.service.CommandeFourService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
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
    public ResponseEntity<CommandeFourDto> mettreAJourEtat(Long idCommandFour, EtatCommande etat) {
        return ResponseEntity.ok(cmndeFourServ.mettreAJourEtat(idCommandFour, etat));
    }

    @Override
    public ResponseEntity<List<LigneCmndeFournisseurDto>> lignes(Long idCommandFour) {
        return ResponseEntity.ok(cmndeFourServ.lignes(idCommandFour));
    }

    @Override
    public ResponseEntity<LigneCmndeFournisseurDto> ajouterLigne(Long idCommandFour, LigneCmndeFournisseurDto ligne) {
        return ResponseEntity.ok(cmndeFourServ.ajouterLigne(idCommandFour, ligne));
    }

    @Override
    public ResponseEntity<LigneCmndeFournisseurDto> modifierQuantite(Long idCommandFour, Long idLigne, BigDecimal quantite) {
        return ResponseEntity.ok(cmndeFourServ.modifierQuantite(idCommandFour, idLigne, quantite));
    }

    @Override
    public ResponseEntity<Void> retirerLigne(Long idCommandFour, Long idLigne) {
        cmndeFourServ.retirerLigne(idCommandFour, idLigne);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity delete(Long idCommandFour) {
        cmndeFourServ.delete(idCommandFour);
        return ResponseEntity.ok().build();
    }
}
