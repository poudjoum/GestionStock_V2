package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneComptageDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.SeanceInventaireDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

/**
 * L'inventaire : confronter ce que le logiciel croit avoir a ce qu'il y a sur l'etagere.
 *
 * Le stock se deduisait de ses mouvements, et cela suffisait tant que rien ne se perdait. Or il se
 * perd — la casse, le vol, la livraison mal saisie, l'article donne sans ticket — et rien ne
 * permettait de le constater ni de le corriger autrement qu'en saisissant des entrees et des
 * sorties a la main, sans dire pourquoi.
 *
 * Une seance se deroule en trois temps, et ils ne se melangent pas : on ouvre, ce qui fige le
 * theorique ; on compte, ce qui n'engage rien ; on valide, et c'est seulement la que le stock
 * bouge. Tant qu'on n'a pas valide, on peut tout reprendre — et l'on peut renoncer entierement.
 */
public interface InventaireService {

    /**
     * Ouvre une seance et y inscrit tout le catalogue, avec le stock de chaque article.
     *
     * Une seule seance ouverte a la fois : deux comptages simultanes figeraient deux fois le meme
     * theorique et produiraient, a la validation, deux corrections dont la seconde defait la
     * premiere.
     */
    SeanceInventaireDto ouvrir(String commentaire);

    /** La seance en cours, ou null s'il n'y en a pas. */
    SeanceInventaireDto seanceOuverte();

    SeanceInventaireDto seance(Long idSeance);

    /** `vue` vaut TOUTES, A_COMPTER ou ECARTS. */
    Page<LigneComptageDto> lignes(Long idSeance, String q, String vue, Pageable pageable);

    /**
     * Note ce qu'on a trouve pour un article.
     *
     * Recompter le meme article remplace la valeur precedente plutot que de s'y ajouter : on
     * recompte une etagere quand on doute du premier passage, et non pour cumuler deux tas.
     */
    LigneComptageDto compter(Long idSeance, Long idArticle, BigDecimal quantite);

    /** Le meme geste, par le code-barres : c'est ce que rend la douchette dans les rayons. */
    LigneComptageDto compterParCode(Long idSeance, String codeArticle, BigDecimal quantite);

    /** Remet une ligne a « pas encore comptee », ce qui n'est pas la meme chose que comptee a zero. */
    LigneComptageDto annulerComptage(Long idSeance, Long idLigne);

    /**
     * Cloture la seance et rattrape les ecarts par des mouvements de stock.
     *
     * Les articles restes sans comptage ne produisent rien : on ne deduit pas d'un rayon qu'on
     * n'a pas regarde qu'il est vide.
     */
    SeanceInventaireDto valider(Long idSeance);

    /** Ferme la seance sans rien corriger. Elle reste lisible : un comptage abandonne est un fait. */
    SeanceInventaireDto abandonner(Long idSeance);

    Page<SeanceInventaireDto> historique(Pageable pageable);
}
