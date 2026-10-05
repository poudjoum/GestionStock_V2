package com.jumpy.tech.gestionstock.gestiondestock.lot;

import com.jumpy.tech.gestionstock.gestiondestock.dto.LotDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.RappelLotDto;

import java.util.List;

public interface LotService {

    /**
     * Les lots d'un article qui ont encore du stock, premier perime en tete. `quantite` est celle du
     * site actif — ce qu'on peut vendre ici —, `parSite` dit ou sont les autres.
     */
    List<LotDto> lotsDeLArticle(Long idArticle);

    /**
     * Ce qui perime ou a perime et reste en rayon : une ligne par lot et par site, la date la plus
     * proche en tete. Le site actif seul, ou tous ceux que l'appelant voit.
     */
    List<LotDto> peremption(boolean tousSites);

    /** Ou il reste du lot, et a qui il a ete vendu. */
    RappelLotDto rappel(Long idLot);
}
