package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.ReapproApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeFourDto;
import com.jumpy.tech.gestionstock.gestiondestock.reappro.CommandesReapproDto;
import com.jumpy.tech.gestionstock.gestiondestock.reappro.ReapproDto;
import com.jumpy.tech.gestionstock.gestiondestock.reappro.ReapproService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ReapproController implements ReapproApi {

    private final ReapproService reapproService;

    public ReapproController(ReapproService reapproService) {
        this.reapproService = reapproService;
    }

    @Override
    public ResponseEntity<ReapproDto> proposition() {
        return ResponseEntity.ok(reapproService.proposition());
    }

    @Override
    public ResponseEntity<List<CommandeFourDto>> creerLesCommandes(CommandesReapproDto commandes) {
        return ResponseEntity.ok(reapproService.creerLesCommandes(commandes));
    }
}
