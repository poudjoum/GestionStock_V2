package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.InscriptionEntrepriseDto;

import java.util.List;

public interface EntrepriseService {
    EntrepriseDto save(EntrepriseDto dto);

    /**
     * Cree une entreprise et le compte qui l'administrera, d'un seul geste.
     *
     * Les deux allaient deja ensemble sans que rien ne les lie : il fallait creer l'entreprise,
     * creer un compte, puis les rattacher — la derniere etape n'etant possible qu'en modifiant la
     * base. Une entreprise sans personne pour y entrer ne sert a rien ; l'une ne se cree donc plus
     * sans l'autre, et dans la meme transaction.
     */
    EntrepriseDto inscrire(InscriptionEntrepriseDto inscription);

    /**
     * L'entreprise du compte connecte.
     *
     * Elle se deduit du jeton et jamais d'un identifiant recu : demander « donne-moi l'entreprise
     * numero 3 » serait rouvrir la porte que le cloisonnement ferme.
     */
    EntrepriseDto mienne();

    EntrepriseDto findById(Long Id);
    List<EntrepriseDto> findAll();
    void delete(Long id);
}
