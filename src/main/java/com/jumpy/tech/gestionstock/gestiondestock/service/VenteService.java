package com.jumpy.tech.gestionstock.gestiondestock.service;


import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface VenteService {
    VenteDto save(VenteDto dto);
    VenteDto findById(Long id);
    List<VenteDto> findAll();
    Page<VenteDto> findAll(Pageable pageable);
    VenteDto findVenteByCode(String codeVente);

    /** Les lignes d'une vente ; VenteDto.fromEntity ne remonte que l'en-tete. */
    List<LigneVenteDto> lignes(Long idVente);

    /**
     * Annule une vente et remet sa marchandise en magasin.
     *
     * La vente n'est pas effacee : une recette encaissee puis rendue doit rester lisible. Le
     * stock, lui, est rattrape par une entree portant le motif ANNULATION_VENTE — sans quoi
     * l'historique montrerait une entree indiscernable d'une livraison.
     */
    VenteDto annuler(Long idVente);

    /**
     * Corrige la quantite d'une ligne deja vendue, et rattrape le stock de la difference.
     *
     * Contrairement a une commande, une vente a deja sorti sa marchandise : on ne peut pas
     * reecrire la ligne et s'en tenir la. Augmenter sort le complement — et echoue si le magasin
     * ne l'a pas ; diminuer remet la difference.
     */
    LigneVenteDto modifierQuantite(Long idVente, Long idLigne, BigDecimal quantite);

    /** Retire une ligne d'une vente et remet sa quantite en magasin. */
    void retirerLigne(Long idVente, Long idLigne);

    void delete(Long id);
}
