package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.MvtStkControllerApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.MotifMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.service.MvtStkService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
        return ResponseEntity.ok(mvtStkService.entreeStock(saisieManuelle(dto, TypeMvtStk.ENTREE)));
    }

    @Override
    public ResponseEntity<MvtStkDto> sortieStock(MvtStkDto dto) {
        return ResponseEntity.ok(mvtStkService.sortieStock(saisieManuelle(dto, TypeMvtStk.SORTIE)));
    }

    /**
     * Un mouvement poste sur ces routes est une saisie a la main. Il peut dire pourquoi — une
     * casse, une peremption, un retour —, mais jamais se faire passer pour un document : le motif
     * d'une livraison ou d'une vente ne se declare pas, il vient de la livraison ou de la vente.
     * Sans quoi l'historique d'un article ne voudrait plus rien dire.
     */
    private MvtStkDto saisieManuelle(MvtStkDto dto, TypeMvtStk sens) {
        if (dto == null) {
            return null;
        }
        MotifMvtStk motif = dto.getMotif();
        if (motif == null) {
            dto.setMotif(MotifMvtStk.SAISIE_MANUELLE);
        } else if (sens == TypeMvtStk.ENTREE ? !motif.saisissableEnEntree() : !motif.saisissableEnSortie()) {
            throw new InvalidEntityException(
                    "Le motif " + motif + " ne se saisit pas à la main pour une " + sens.name().toLowerCase(),
                    ErrorCodes.MVT_STK_NOT_VALID,
                    List.of(Arrays.stream(MotifMvtStk.values())
                            .filter(m -> sens == TypeMvtStk.ENTREE ? m.saisissableEnEntree() : m.saisissableEnSortie())
                            .map(Enum::name)
                            .collect(Collectors.joining(", ", "Motifs possibles : ", ""))));
        }
        return dto;
    }
}
