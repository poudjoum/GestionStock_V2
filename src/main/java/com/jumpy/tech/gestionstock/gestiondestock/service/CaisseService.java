package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.EtatDeCaisseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ReglementDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

/**
 * L'etat de la caisse.
 *
 * Les encaissements existaient sans que rien ne les additionne : savoir ce qui etait entre dans la
 * journee demandait d'ouvrir les factures une par une. C'est pourtant la premiere question du
 * soir, quand on ferme.
 */
public interface CaisseService {

    /**
     * Ce qui est entre entre deux dates, bornes comprises, avec le detail par moyen de paiement.
     *
     * Sans dates, la journee en cours : c'est l'usage courant, et l'imposer a chaque appel n'aurait
     * fait qu'alourdir le geste quotidien.
     */
    EtatDeCaisseDto etat(LocalDate debut, LocalDate fin);

    /** Le detail des encaissements de la periode, du plus recent au plus ancien. */
    Page<ReglementDto> reglements(LocalDate debut, LocalDate fin, Pageable pageable);
}
