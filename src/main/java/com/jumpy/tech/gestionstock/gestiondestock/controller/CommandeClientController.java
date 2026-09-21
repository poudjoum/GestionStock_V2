package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.CommandeClientApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ClotureDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCommandeClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;
import com.jumpy.tech.gestionstock.gestiondestock.service.CommandeClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
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
    public ResponseEntity<CommandeClientDto> mettreAJourEtat(Long idCommandClient, EtatCommande etat) {
        return ResponseEntity.ok(cmdeCliService.mettreAJourEtat(idCommandClient, etat));
    }

    @Override
    public ResponseEntity<CommandeClientDto> cloturer(Long idCommandClient, ClotureDto cloture) {
        return ResponseEntity.ok(cmdeCliService.cloturer(idCommandClient,
                cloture == null ? null : cloture.getMotif()));
    }

    @Override
    public ResponseEntity<List<LigneCommandeClientDto>> lignes(Long idCommandClient) {
        return ResponseEntity.ok(cmdeCliService.lignes(idCommandClient));
    }

    @Override
    public ResponseEntity<LigneCommandeClientDto> ajouterLigne(Long idCommandClient, LigneCommandeClientDto ligne) {
        return ResponseEntity.ok(cmdeCliService.ajouterLigne(idCommandClient, ligne));
    }

    @Override
    public ResponseEntity<LigneCommandeClientDto> modifierQuantite(Long idCommandClient, Long idLigne, BigDecimal quantite) {
        return ResponseEntity.ok(cmdeCliService.modifierQuantite(idCommandClient, idLigne, quantite));
    }

    @Override
    public ResponseEntity<Void> retirerLigne(Long idCommandClient, Long idLigne) {
        cmdeCliService.retirerLigne(idCommandClient, idLigne);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity delete(Long idCommandClient) {
        cmdeCliService.delete(idCommandClient);
        return ResponseEntity.ok().build();
    }
}
