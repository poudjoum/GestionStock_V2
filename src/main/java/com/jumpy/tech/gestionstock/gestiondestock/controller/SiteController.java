package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.SiteApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MesSitesDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.SeuilDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.SiteDto;
import com.jumpy.tech.gestionstock.gestiondestock.service.SiteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SiteController implements SiteApi {

    private final SiteService siteService;

    public SiteController(SiteService siteService) {
        this.siteService = siteService;
    }

    @Override
    public ResponseEntity<List<SiteDto>> sites() {
        return ResponseEntity.ok(siteService.sites());
    }

    @Override
    public ResponseEntity<MesSitesDto> mesSites() {
        return ResponseEntity.ok(siteService.mesSites());
    }

    @Override
    public ResponseEntity<SiteDto> creer(SiteDto dto) {
        return ResponseEntity.ok(siteService.creer(dto));
    }

    @Override
    public ResponseEntity<SiteDto> modifier(Long idSite, SiteDto dto) {
        return ResponseEntity.ok(siteService.modifier(idSite, dto));
    }

    @Override
    public ResponseEntity<SiteDto> fermer(Long idSite) {
        return ResponseEntity.ok(siteService.fermer(idSite));
    }

    @Override
    public ResponseEntity<Void> definirSeuil(Long idArticle, Long idSite, SeuilDto seuil) {
        siteService.definirSeuil(idArticle, idSite, seuil == null ? null : seuil.seuil());
        return ResponseEntity.noContent().build();
    }
}
