package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeFourDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCmndeFournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneReceptionDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;

import java.math.BigDecimal;
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

    /**
     * Enregistre ce qui est reellement arrive, ligne par ligne.
     *
     * Une commande se soldait d'un coup : recevoir 6 unites sur 10 obligeait a mentir en declarant
     * tout livre, ou a ne rien enregistrer en attendant le reste. L'etat n'est pas declare mais
     * constate — tout est arrive, la commande est livree ; il manque quelque chose, elle est
     * partiellement livree.
     */
    CommandeFourDto recevoir(Long id, List<LigneReceptionDto> receptions);

    /**
     * Les lignes d'une commande.
     *
     * Elles ne figurent pas dans CommandeFourDto.fromEntity, qui ne remonte que l'en-tete : une
     * commande se lit souvent en liste, et charger les lignes de chacune pour les jeter aussitot
     * coute plus que de les demander quand on en a besoin.
     */
    List<LigneCmndeFournisseurDto> lignes(Long idCommande);

    /**
     * Les trois operations qui manquaient : une commande enregistree ne se corrigeait pas.
     *
     * Elles n'ecrivent aucun mouvement de stock, et n'en ont pas besoin : la marchandise n'entre
     * qu'a la livraison, qui relit les lignes telles qu'elles sont a ce moment-la. Modifier une
     * commande avant sa livraison n'a donc rien a rattraper — c'est le cycle de vie qui rend ces
     * operations simples.
     */
    LigneCmndeFournisseurDto ajouterLigne(Long idCommande, LigneCmndeFournisseurDto ligne);

    LigneCmndeFournisseurDto modifierQuantite(Long idCommande, Long idLigne, BigDecimal quantite);

    void retirerLigne(Long idCommande, Long idLigne);

    void delete(Long id);
}
