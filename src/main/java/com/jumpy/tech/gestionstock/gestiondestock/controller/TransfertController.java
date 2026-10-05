package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.TransfertApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneTransfertDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ReceptionTransfertDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.TransfertDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatTransfert;
import com.jumpy.tech.gestionstock.gestiondestock.service.TransfertService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TransfertController implements TransfertApi {

    private final TransfertService transfertService;

    public TransfertController(TransfertService transfertService) {
        this.transfertService = transfertService;
    }

    @Override
    public ResponseEntity<List<TransfertDto>> lister(EtatTransfert etat) {
        return ResponseEntity.ok(transfertService.lister(etat));
    }

    @Override
    public ResponseEntity<TransfertDto> detail(Long id) {
        return ResponseEntity.ok(transfertService.detail(id));
    }

    @Override
    public ResponseEntity<TransfertDto> creer(TransfertDto dto) {
        return ResponseEntity.ok(transfertService.creer(dto));
    }

    @Override
    public ResponseEntity<TransfertDto> ajouterLigne(Long id, LigneTransfertDto ligne) {
        return ResponseEntity.ok(transfertService.ajouterLigne(id, ligne));
    }

    @Override
    public ResponseEntity<TransfertDto> retirerLigne(Long id, Long idLigne) {
        return ResponseEntity.ok(transfertService.retirerLigne(id, idLigne));
    }

    @Override
    public ResponseEntity<TransfertDto> expedier(Long id) {
        return ResponseEntity.ok(transfertService.expedier(id));
    }

    @Override
    public ResponseEntity<TransfertDto> recevoir(Long id, List<ReceptionTransfertDto> receptions) {
        return ResponseEntity.ok(transfertService.recevoir(id, receptions));
    }

    @Override
    public ResponseEntity<TransfertDto> annuler(Long id) {
        return ResponseEntity.ok(transfertService.annuler(id));
    }
}
