package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.CommerceDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ResumePlateformeDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

/**
 * La plateforme, vue par l'editeur.
 *
 * Tout ici passe au-dessus du cloisonnement, qui protege chaque commerce des autres : c'est le
 * seul ensemble de routes dans ce cas, et il est reserve au super-administrateur — par la chaine
 * de securite, et une seconde fois par le service lui-meme.
 */
@Tag(name = "Plateforme", description = "Les commerces hébergés, leur abonnement et leur activité")
public interface PlateformeApi {

    @Operation(summary = "Les commerces et leur activité, le plus récemment inscrit d'abord")
    @GetMapping(value = APP_ROOT + "/plateforme/commerces", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<CommerceDto>> commerces();

    @Operation(summary = "Les quelques nombres qui tiennent en haut du tableau de bord")
    @GetMapping(value = APP_ROOT + "/plateforme/resume", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ResumePlateformeDto> resume();

    @Operation(summary = "Ferme l'accès d'un commerce et révoque les jetons de ses comptes")
    @PostMapping(value = APP_ROOT + "/plateforme/commerces/{idCommerce}/suspension",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CommerceDto> suspendre(@PathVariable Long idCommerce);

    @Operation(summary = "Rouvre l'accès d'un commerce suspendu")
    @PostMapping(value = APP_ROOT + "/plateforme/commerces/{idCommerce}/reprise",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CommerceDto> reprendre(@PathVariable Long idCommerce);

    @Operation(summary = "Reporte l'échéance d'un an")
    @PostMapping(value = APP_ROOT + "/plateforme/commerces/{idCommerce}/renouvellement",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CommerceDto> renouveler(@PathVariable Long idCommerce);

    @Operation(summary = "Fixe l'échéance à une date choisie ; sans date, retire l'abonnement")
    @PostMapping(value = APP_ROOT + "/plateforme/commerces/{idCommerce}/echeance",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CommerceDto> fixerEcheance(
            @PathVariable Long idCommerce,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate echeance);
}
