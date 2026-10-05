package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeFourDto;
import com.jumpy.tech.gestionstock.gestiondestock.reappro.CommandesReapproDto;
import com.jumpy.tech.gestionstock.gestiondestock.reappro.ReapproDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

public interface ReapproApi {

    /** Ce qu'il faudrait commander pour le site actif, au rythme des ventes. */
    @Tag(name = "Get", description = "Get Methods of Gestion de Stock APIs")
    @GetMapping(value = APP_ROOT + "/reappro", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ReapproDto> proposition();

    /** Les lignes retenues deviennent des commandes en preparation, une par fournisseur. */
    @Tag(name = "Post", description = "Post Methods of Gestion de Stock APIs")
    @PostMapping(value = APP_ROOT + "/reappro/commandes", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<CommandeFourDto>> creerLesCommandes(@RequestBody CommandesReapproDto commandes);
}
