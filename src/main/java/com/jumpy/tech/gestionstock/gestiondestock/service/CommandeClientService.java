package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCommandeClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;

import java.math.BigDecimal;
import java.util.List;

// Cette interface de service tirait les annotations web de Spring et Swagger — @GetMapping,
// @RequestBody, APP_ROOT — sans en employer aucune : des imports de la couche HTTP dans le
// contrat metier.
public interface CommandeClientService {

    /**
     * Fait avancer la commande dans son cycle de vie.
     *
     * Aucune transition ne touche au stock, pas meme la livraison : c'est la vente qui sort la
     * marchandise du magasin. Decompter ici aussi la retirerait deux fois.
     */
    CommandeClientDto mettreAJourEtat(Long id, EtatCommande etat);

    /** Les lignes d'une commande ; CommandeClientDto.fromEntity ne remonte que l'en-tete. */
    List<LigneCommandeClientDto> lignes(Long idCommande);

    /**
     * Correction des lignes, possible tant que la commande n'est pas figee. Aucun mouvement de
     * stock n'en decoule : une commande client n'en ecrit jamais.
     */
    LigneCommandeClientDto ajouterLigne(Long idCommande, LigneCommandeClientDto ligne);

    LigneCommandeClientDto modifierQuantite(Long idCommande, Long idLigne, BigDecimal quantite);

    void retirerLigne(Long idCommande, Long idLigne);

    CommandeClientDto save(CommandeClientDto dto);
    CommandeClientDto findById(Long id);
    CommandeClientDto findByCode(String code);
    List<CommandeClientDto> findAll();
    void delete(Long id);
}
