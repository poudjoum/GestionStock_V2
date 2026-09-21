package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.MvtStkControllerApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.MotifMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.service.MvtStkService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
public class MvtStkController implements MvtStkControllerApi {

    private final MvtStkService mvtStkService;

    public MvtStkController(MvtStkService mvtStkService) {
        this.mvtStkService = mvtStkService;
    }

    @Override
    public ResponseEntity<BigDecimal> stockReelArticle(Long idArticle) {
        return ResponseEntity.ok(mvtStkService.stockReelArticle(idArticle));
    }

    @Override
    public ResponseEntity<List<MvtStkDto>> mvtStkArticle(Long idArticle) {
        return ResponseEntity.ok(mvtStkService.mvtStkArticle(idArticle));
    }

    @Override
    public ResponseEntity<MvtStkDto> entreeStock(MvtStkDto dto) {
        return ResponseEntity.ok(mvtStkService.entreeStock(saisieManuelle(dto)));
    }

    @Override
    public ResponseEntity<MvtStkDto> sortieStock(MvtStkDto dto) {
        return ResponseEntity.ok(mvtStkService.sortieStock(saisieManuelle(dto)));
    }

    /**
     * Un mouvement poste sur ces routes est une saisie a la main, quoi qu'en dise le corps de la
     * requete. Le motif se decide comme le sens : par ce qui a reellement eu lieu, et non par ce
     * que l'appelant declare — sinon une saisie pourrait se faire passer pour une livraison et
     * l'historique ne voudrait plus rien dire.
     */
    private MvtStkDto saisieManuelle(MvtStkDto dto) {
        if (dto != null) {
            dto.setMotif(MotifMvtStk.SAISIE_MANUELLE);
        }
        return dto;
    }
}
