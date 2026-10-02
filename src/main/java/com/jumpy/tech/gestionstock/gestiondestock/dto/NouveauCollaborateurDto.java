package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Un collaborateur que le gerant ajoute a son equipe : un caissier, un magasinier, un comptable.
 *
 * Ce n'est pas `UserDto` : celui-ci exige une date de naissance et une adresse complete, que
 * personne ne demande a un caissier embauche le matin pour tenir la caisse l'apres-midi. Reste ce
 * sans quoi le compte n'existe pas — un nom, un identifiant, un mot de passe provisoire, un role —
 * et un telephone pour le joindre. Le courriel est facultatif : il ne sert qu'a prevenir.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NouveauCollaborateurDto {

    private String nom;
    private String prenoms;
    private String username;
    private String email;
    private String numTel;

    /** Provisoire : le collaborateur en choisira un autre a sa premiere connexion. */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String motDePasse;

    private List<ERole> roles;
}
