package com.jumpy.tech.gestionstock.gestiondestock.reservation;

import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneCmndeClientRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Le stock reserve : ce que les commandes clients validees retiennent dans le site qui les livre.
 *
 * Une commande reserve des sa validation, et jusqu'a ce qu'elle soit servie, close ou annulee :
 * une commande en preparation n'engage encore personne. Le comptoir ne vend que le disponible —
 * le stock moins le reserve —, sans quoi le client qui a commande lundi trouverait vendredi les
 * rayons vides.
 *
 * Rien n'est stocke : la reservation se lit dans les lignes des commandes, comme le stock dans
 * ses mouvements.
 */
@Component
public class Reservations {

    /** Les commandes qui retiennent de la marchandise. */
    public static final List<EtatCommande> EN_COURS = List.of(EtatCommande.VALIDEE, EtatCommande.PARTIELLEMENT_LIVREE);

    private final LigneCmndeClientRepository lignes;

    public Reservations(LigneCmndeClientRepository lignes) {
        this.lignes = lignes;
    }

    /** Ce qui est reserve de l'article dans le site, hors la commande `exclue` (nulle : toutes). */
    public BigDecimal dansSite(Long idArticle, Long idSite, Long exclue) {
        if (idSite == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal reserve = lignes.reserveDansSite(idArticle, idSite, EN_COURS, exclue);
        return reserve == null ? BigDecimal.ZERO : reserve.max(BigDecimal.ZERO);
    }

    /** Article, puis site, puis quantite reservee. */
    public Map<Long, Map<Long, BigDecimal>> parArticleEtSite(Collection<Long> idsArticles) {
        Map<Long, Map<Long, BigDecimal>> reserves = new HashMap<>();
        if (idsArticles.isEmpty()) {
            return reserves;
        }
        for (Object[] ligne : lignes.reservesParSite(idsArticles, EN_COURS)) {
            if (ligne[1] != null) {
                reserves.computeIfAbsent((Long) ligne[0], k -> new HashMap<>()).put((Long) ligne[1], (BigDecimal) ligne[2]);
            }
        }
        return reserves;
    }
}
