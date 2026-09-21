package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.EtatDuStockDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneInventaireDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

public interface StockApi {

    /** Ce que vaut le magasin, et combien d'articles y manquent. */
    @Tag(name = "Get", description = "Get Methods of Gestion de Stock APIs")
    @GetMapping(value = APP_ROOT + "/stock/etat", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<EtatDuStockDto> etat();

    /** L'inventaire, article par article : `?page=0&size=20`. */
    @GetMapping(value = APP_ROOT + "/stock/inventaire", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Page<LigneInventaireDto>> inventaire(Pageable pageable);

    /** Ce qu'il faut recommander : la liste qu'on emporte chez le fournisseur. */
    @GetMapping(value = APP_ROOT + "/stock/alertes", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<LigneInventaireDto>> alertes();
}
