package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.FactureDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
     * Annule une facture.
     *
     * Elle n'est pas supprimee — un numero emis puis disparu est exactement ce qu'une
     * comptabilite ne doit pas montrer. L'annulation rouvre en revanche la vente a la correction.
     */
    FactureDto annuler(Long id);
}
