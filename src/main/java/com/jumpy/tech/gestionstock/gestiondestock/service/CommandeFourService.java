package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeFourDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;

import java.util.List;

public interface CommandeFourService {
    CommandeFourDto save( CommandeFourDto dto);
    CommandeFourDto findById(Long id);
    CommandeFourDto findByCode(String code);
    List< CommandeFourDto> findAll();

    /**
     * Fait avancer la commande dans son cycle de vie. Le passage en LIVREE fait entrer la
     * marchandise en magasin ; c'est la seule transition qui touche au stock.
     */
    CommandeFourDto mettreAJourEtat(Long id, EtatCommande etat);

    void delete(Long id);
}
