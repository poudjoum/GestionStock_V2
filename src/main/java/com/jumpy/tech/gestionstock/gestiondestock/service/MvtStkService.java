package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mouvements de stock.
 *
 * Cette couche manquait entierement : l'entite MvtStk et son repository existaient depuis le
 * premier commit, sans que rien ne les ecrive ni ne les lise. Une vente ne retirait donc aucune
 * quantite du magasin, et le stock disponible d'un article n'etait calcule nulle part.
 */
public interface MvtStkService {

    /** Entrees moins sorties. Zero si l'article n'a jamais bouge. */
    BigDecimal stockReelArticle(Long idArticle);

    /** L'historique d'un article, du plus recent au plus ancien. */
    List<MvtStkDto> mvtStkArticle(Long idArticle);

    MvtStkDto entreeStock(MvtStkDto dto);

    /**
     * Refuse la sortie si elle depasse le stock reel : accepter revient a inscrire en magasin une
     * quantite negative, que le comptoir decouvrirait en cherchant une marchandise absente.
     */
    MvtStkDto sortieStock(MvtStkDto dto);

    /**
     * Une sortie qui a deja eu lieu ailleurs : on l'enregistre telle qu'elle s'est produite.
     *
     * Deux differences avec `sortieStock`, et une seule raison : la marchandise est deja partie.
     *
     * La date est celle de la sortie, pas celle de l'enregistrement — une vente de 9 h
     * synchronisee a midi doit peser sur la caisse de 9 h.
     *
     * Le stock ne s'y oppose pas. Deux caisses vendent hors ligne le dernier sac de ciment ; la
     * seconde synchronise et serait refusee, alors que le sac est parti. Refuser n'empecherait
     * rien : cela effacerait seulement la trace de ce qui a eu lieu. Le stock passe donc sous
     * zero, et c'est precisement le signal qu'un inventaire est a faire sur cet article.
     *
     * Cette methode n'est jamais atteinte par une route HTTP. Les deux routes publiques de
     * mouvement restent datees par le serveur et opposables au stock : laisser un appelant
     * quelconque antidater une sortie permettrait de fabriquer un stock qui n'a jamais existe.
     */
    MvtStkDto sortieConstatee(MvtStkDto dto, java.time.Instant quand);
}
