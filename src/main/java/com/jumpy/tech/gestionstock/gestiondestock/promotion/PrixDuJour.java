package com.jumpy.tech.gestionstock.gestiondestock.promotion;

import com.jumpy.tech.gestionstock.gestiondestock.entities.PromotionArticle;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeRemise;
import com.jumpy.tech.gestionstock.gestiondestock.repository.PromotionArticleRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Le prix auquel un article se vend un jour donne : celui de sa promotion s'il en a une.
 *
 * C'est le serveur qui decide, et non la caisse : une caisse dont la copie des promotions est
 * ancienne — rechargee avant le debut de la campagne — vendrait sinon au prix normal un article
 * que le magasin affiche en promotion.
 */
@Service
public class PrixDuJour {

    private final PromotionArticleRepository promotions;
    private final Calendrier calendrier;

    public PrixDuJour(PromotionArticleRepository promotions, Calendrier calendrier) {
        this.promotions = promotions;
        this.calendrier = calendrier;
    }

    /** Les promotions d'un magasin a cet instant, par article. */
    public Map<Long, PromotionArticle> promotions(Long idEntreprise, Instant quand) {
        if (idEntreprise == null) {
            return Map.of();
        }
        return promotions.enCours(idEntreprise, calendrier.jourDe(quand)).stream()
                .collect(Collectors.toMap(p -> p.getArticle().getId(), Function.identity(), (a, b) -> a));
    }

    /**
     * Le prix d'une ligne vendue maintenant, au comptoir.
     *
     * Le prix envoye par la caisse est garde s'il est plus bas : le serveur applique la
     * promotion qu'elle aurait oubliee, il ne reprend pas une remise qu'elle aurait accordee.
     */
    /**
     * Le prix d'une ligne saisie dans un conditionnement : la remise en pourcentage s'y applique,
     * le prix fixe non. Un prix fixe est celui de l'unite — l'appliquer au carton le vendrait au
     * prix d'une bouteille.
     */
    public static BigDecimal pourLigne(BigDecimal prixEnvoye, BigDecimal prixCatalogue, PromotionArticle promotion,
                                       boolean conditionne) {
        boolean applicable = promotion != null
                && (!conditionne || promotion.getTypeRemise() != TypeRemise.PRIX_FIXE);
        return pourLigne(prixEnvoye, prixCatalogue, applicable ? promotion : null);
    }

    public static BigDecimal pourLigne(BigDecimal prixEnvoye, BigDecimal prixCatalogue, PromotionArticle promotion) {
        BigDecimal prix = prixEnvoye != null ? prixEnvoye : prixCatalogue;
        if (promotion == null || prixCatalogue == null) {
            return prix;
        }
        BigDecimal promo = PrixPromotionnel.prix(prixCatalogue, promotion.getTypeRemise(), promotion.getValeur());
        return prix == null ? promo : prix.min(promo);
    }
}
