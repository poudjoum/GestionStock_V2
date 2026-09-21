package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.FactureDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

public interface FactureApi {

    /**
     * Emet la facture d'une vente. `POST` sur la vente et non sur `/factures` : c'est la vente
     * qui commande, et une facture ne s'invente pas hors d'elle.
     */
    @Tag(name = "Post", description = "Post Methods of Gestion de Stock APIs")
    @PostMapping(value = APP_ROOT + "/ventes/{idVente}/facture", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<FactureDto> emettre(@PathVariable Long idVente);

    @Tag(name = "Get", description = "Get Methods of Gestion de Stock APIs")
    @GetMapping(value = APP_ROOT + "/ventes/{idVente}/facture", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<FactureDto> factureDeLaVente(@PathVariable Long idVente);

    @GetMapping(value = APP_ROOT + "/factures/{idFacture}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<FactureDto> findById(@PathVariable Long idFacture);

    @GetMapping(value = APP_ROOT + "/factures/numero/{numero}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<FactureDto> findByNumero(@PathVariable String numero);

    /** Liste paginee : `?page=0&size=20&sort=dateEmission,desc`. */
    @GetMapping(value = APP_ROOT + "/factures", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Page<FactureDto>> findAll(Pageable pageable);

    /** Annule la facture sans la supprimer, et rouvre la vente a la correction. */
    @PostMapping(value = APP_ROOT + "/factures/{idFacture}/annulation", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<FactureDto> annuler(@PathVariable Long idFacture);
}
