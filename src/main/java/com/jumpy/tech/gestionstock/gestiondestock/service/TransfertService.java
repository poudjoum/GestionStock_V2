package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneTransfertDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ReceptionTransfertDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.TransfertDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatTransfert;

import java.util.List;

/**
 * Les transferts entre sites, en deux temps : l'expedition sort la marchandise du site de depart,
 * la reception fait entrer au site d'arrivee ce qui y est compte.
 */
public interface TransfertService {

    /** Les transferts qui partent des sites de l'appelant ou y arrivent ; `etat` facultatif. */
    List<TransfertDto> lister(EtatTransfert etat);

    TransfertDto detail(Long id);

    /** Un brouillon : rien ne bouge tant qu'il n'est pas expedie. */
    TransfertDto creer(TransfertDto dto);

    TransfertDto ajouterLigne(Long id, LigneTransfertDto ligne);

    TransfertDto retirerLigne(Long id, Long idLigne);

    /** Le depart : le stock du site source diminue, la marchandise est en transit. */
    TransfertDto expedier(Long id);

    /**
     * L'arrivee : le site de destination recoit ce qu'il a compte. Un ecart se motive ; ce qui
     * manque ne revient pas au depart — il a quitte le depot, il est perdu en route.
     */
    TransfertDto recevoir(Long id, List<ReceptionTransfertDto> receptions);

    /** Abandonne un brouillon. Une fois parti, un transfert se recoit, meme a zero. */
    TransfertDto annuler(Long id);
}
