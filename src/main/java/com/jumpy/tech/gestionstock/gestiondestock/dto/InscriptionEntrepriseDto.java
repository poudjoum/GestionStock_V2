package com.jumpy.tech.gestionstock.gestiondestock.dto;

import lombok.Data;

/**
 * Une entreprise et le compte qui l'administrera.
 *
 * Les deux vont ensemble : une entreprise sans personne pour y entrer ne sert a rien, et c'est
 * pourtant ce que produisait la creation d'entreprise jusqu'ici — il fallait ensuite creer un
 * compte, puis le rattacher a la main.
 */
@Data
public class InscriptionEntrepriseDto {

    private EntrepriseDto entreprise;

    /** Le premier administrateur. Son mot de passe est chiffre a l'enregistrement. */
    private UserDto administrateur;
}
