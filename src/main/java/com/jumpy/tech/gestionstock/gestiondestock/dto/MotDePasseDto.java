package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Un changement de mot de passe.
 *
 * `ancien` n'est rempli que par le titulaire qui change le sien ; un administrateur qui
 * reinitialise ne le connait pas, et n'a pas a le connaitre.
 *
 * Les deux champs n'existent qu'en entree : les rendre serait les ecrire dans les journaux du
 * premier intermediaire qui enregistre les reponses.
 */
@Data
public class MotDePasseDto {

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String ancien;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String nouveau;
}
