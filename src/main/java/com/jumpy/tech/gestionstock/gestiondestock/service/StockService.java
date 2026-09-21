package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.EtatDuStockDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneInventaireDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * L'etat du magasin.
 *
 * Le stock se lisait article par article : personne ne pouvait dire ce que valait l'ensemble, ni
 * quels articles s'epuisaient. La rupture se decouvrait au comptoir, devant le client.
 */
public interface StockService {

    /** Ce que vaut le magasin, et combien d'articles y manquent. */
    EtatDuStockDto etat();

    /** L'inventaire, article par article. */
    Page<LigneInventaireDto> inventaire(Pageable pageable);

    /**
     * Les seuls articles a recommander : ceux qui sont tombes a zero, et ceux passes sous leur
     * seuil. C'est la liste qu'on emporte chez le fournisseur.
     */
    List<LigneInventaireDto> alertes();
}
