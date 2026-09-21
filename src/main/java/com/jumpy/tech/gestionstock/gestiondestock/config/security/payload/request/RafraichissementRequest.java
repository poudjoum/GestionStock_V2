package com.jumpy.tech.gestionstock.gestiondestock.config.security.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Le jeton de rafraichissement qu'on presente, pour en obtenir un nouveau ou pour se deconnecter.
 *
 * Dans le corps et non dans l'URL : un jeton dans un chemin se recopie dans les journaux d'acces
 * de tout ce qui se trouve entre le telephone et le serveur.
 */
@Data
public class RafraichissementRequest {

    @NotBlank(message = "Le jeton de rafraîchissement est obligatoire")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String refreshToken;
}
