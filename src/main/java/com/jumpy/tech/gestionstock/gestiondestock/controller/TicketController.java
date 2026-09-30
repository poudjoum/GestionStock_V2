package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.dto.TicketPublicDto;
import com.jumpy.tech.gestionstock.gestiondestock.fidelite.TicketsPublics;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

/**
 * Le ticket d'un client, retrouve par le code de son QR.
 *
 * La seule route de lecture ouverte sans compte : le client qui scanne son ticket n'en a pas.
 */
@RestController
public class TicketController {

    private final TicketsPublics tickets;

    public TicketController(TicketsPublics tickets) {
        this.tickets = tickets;
    }

    @GetMapping(value = APP_ROOT + "/tickets/{code}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TicketPublicDto> ticket(@PathVariable String code) {
        // Jamais en cache partage : un ticket annule entre-temps doit le dire au prochain scan.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(tickets.parCode(code));
    }
}
