package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneTransfertDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ReceptionTransfertDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.TransfertDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatTransfert;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

@Tag(name = "Transferts", description = "Envois de marchandise entre les sites")
public interface TransfertApi {

    /** `?etat=EXPEDIE` : ce qui est en route. */
    @GetMapping(value = APP_ROOT + "/transferts", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<TransfertDto>> lister(@RequestParam(required = false) EtatTransfert etat);

    @GetMapping(value = APP_ROOT + "/transferts/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TransfertDto> detail(@PathVariable("id") Long id);

    @PostMapping(value = APP_ROOT + "/transferts", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TransfertDto> creer(@RequestBody TransfertDto dto);

    @PostMapping(value = APP_ROOT + "/transferts/{id}/lignes", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TransfertDto> ajouterLigne(@PathVariable("id") Long id, @RequestBody LigneTransfertDto ligne);

    @DeleteMapping(value = APP_ROOT + "/transferts/{id}/lignes/{idLigne}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TransfertDto> retirerLigne(@PathVariable("id") Long id, @PathVariable("idLigne") Long idLigne);

    @PostMapping(value = APP_ROOT + "/transferts/{id}/expedition", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TransfertDto> expedier(@PathVariable("id") Long id);

    /** Corps facultatif : sans lui, tout ce qui est parti est arrive. */
    @PostMapping(value = APP_ROOT + "/transferts/{id}/reception", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TransfertDto> recevoir(@PathVariable("id") Long id,
                                          @RequestBody(required = false) List<ReceptionTransfertDto> receptions);

    @PostMapping(value = APP_ROOT + "/transferts/{id}/annulation", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TransfertDto> annuler(@PathVariable("id") Long id);
}
