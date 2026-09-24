package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.ComptageDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneComptageDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.OuvertureInventaireDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.SeanceInventaireDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

/**
 * L'inventaire : compter le magasin, voir les ecarts, les rattraper.
 *
 * Trois temps qui ne se melangent pas — ouvrir, compter, valider — et le stock ne bouge qu'au
 * dernier. Tant qu'on n'a pas valide, tout se reprend, et l'on peut renoncer entierement.
 */
@Tag(name = "Inventaire", description = "Comptage du magasin et rattrapage des écarts")
public interface InventaireApi {

    @Operation(summary = "Ouvre une séance et y inscrit tout le catalogue avec son stock théorique")
    @PostMapping(value = APP_ROOT + "/inventaires", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<SeanceInventaireDto> ouvrir(@RequestBody(required = false) OuvertureInventaireDto ouverture);

    /**
     * La seance en cours.
     *
     * Rend 204 quand il n'y en a pas : ne pas avoir d'inventaire ouvert est l'etat normal d'un
     * magasin, et non une erreur a signaler.
     */
    @Operation(summary = "La séance en cours, ou 204 s'il n'y en a pas")
    @GetMapping(value = APP_ROOT + "/inventaires/ouverte", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<SeanceInventaireDto> seanceOuverte();

    @Operation(summary = "Les séances passées, la plus récente d'abord")
    @GetMapping(value = APP_ROOT + "/inventaires", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Page<SeanceInventaireDto>> historique(
            @PageableDefault(size = 25, sort = "dateOuverture",
                    direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable);

    @Operation(summary = "Une séance et ses compteurs")
    @GetMapping(value = APP_ROOT + "/inventaires/{idSeance}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<SeanceInventaireDto> seance(@PathVariable Long idSeance);

    @Operation(summary = "Les lignes d'une séance ; `vue` vaut TOUTES, A_COMPTER ou ECARTS")
    @GetMapping(value = APP_ROOT + "/inventaires/{idSeance}/lignes", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Page<LigneComptageDto>> lignes(
            @PathVariable Long idSeance,
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false, defaultValue = "TOUTES") String vue,
            @PageableDefault(size = 50, sort = "designation") Pageable pageable);

    @Operation(summary = "Note ce qu'on a trouvé pour un article, par identifiant ou par code-barres")
    @PostMapping(value = APP_ROOT + "/inventaires/{idSeance}/comptages",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<LigneComptageDto> compter(@PathVariable Long idSeance,
                                             @RequestBody ComptageDto comptage);

    @Operation(summary = "Remet une ligne à « pas encore comptée »")
    @DeleteMapping(value = APP_ROOT + "/inventaires/{idSeance}/comptages/{idLigne}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<LigneComptageDto> annulerComptage(@PathVariable Long idSeance,
                                                     @PathVariable Long idLigne);

    @Operation(summary = "Clôture la séance et rattrape les écarts par des mouvements de stock")
    @PostMapping(value = APP_ROOT + "/inventaires/{idSeance}/validation",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<SeanceInventaireDto> valider(@PathVariable Long idSeance);

    @Operation(summary = "Ferme la séance sans rien corriger")
    @PostMapping(value = APP_ROOT + "/inventaires/{idSeance}/abandon",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<SeanceInventaireDto> abandonner(@PathVariable Long idSeance);
}
