package com.jumpy.tech.gestionstock.gestiondestock.reappro;

import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeFourDto;

import java.util.List;

public interface ReapproService {

    /** Ce qu'il faudrait commander pour le site actif. */
    ReapproDto proposition();

    /** Une commande en preparation par fournisseur, livree au site actif. Rien n'est valide. */
    List<CommandeFourDto> creerLesCommandes(CommandesReapproDto commandes);
}
