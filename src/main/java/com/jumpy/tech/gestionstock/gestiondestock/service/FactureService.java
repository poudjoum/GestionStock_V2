package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.FactureDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ReglementDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Facturation des ventes.
 *
 * Rien ne calculait de montant dans cette application : la vente portait des lignes, chacune une
 * quantite et un prix, et personne n'en faisait jamais la somme. Une facture est ce calcul, fige
 * au moment ou on l'emet.
 */
public interface FactureService {

    /**
     * Emet la facture d'une vente.
     *
     * Une vente ne se facture qu'une fois, et pas si elle est annulee : on ne reclame pas le
     * paiement d'une marchandise rendue.
     */
    FactureDto emettre(Long idVente);

    FactureDto findById(Long id);

    FactureDto findByNumero(String numero);

    /** La facture d'une vente, si elle a ete emise. */
    FactureDto findByVente(Long idVente);

    Page<FactureDto> findAll(Pageable pageable);

    /**
     * Les factures, filtrables.
     *
     * `q` porte sur le numero et le nom du client ; `statut` vaut IMPAYEE,
     * PARTIELLEMENT_REGLEE, REGLEE, ANNULEE, ou DUES — ce dernier reunissant tout ce sur quoi il
     * reste a encaisser, qui est la question que le comptable pose vraiment.
     */
    Page<FactureDto> rechercher(String q, String statut, Pageable pageable);

    /**
     * Annule une facture.
     *
     * Elle n'est pas supprimee — un numero emis puis disparu est exactement ce qu'une
     * comptabilite ne doit pas montrer. L'annulation rouvre en revanche la vente a la correction.
     */
    FactureDto annuler(Long id);

    /**
     * Enregistre un encaissement sur une facture.
     *
     * Plusieurs reglements peuvent porter sur la meme facture : un acompte puis le solde est le
     * cas ordinaire. Ce qui depasse le reste a payer est refuse — un trop-percu est une erreur de
     * saisie, pas une situation a enregistrer.
     */
    ReglementDto regler(Long idFacture, ReglementDto reglement);

    /** Les encaissements d'une facture, du plus ancien au plus recent. */
    List<ReglementDto> reglements(Long idFacture);

    /**
     * Efface un encaissement saisi par erreur.
     *
     * C'est un geste comptable, et le seul recours : un reglement ne se modifie pas, il se reprend.
     */
    void supprimerReglement(Long idFacture, Long idReglement);
}
