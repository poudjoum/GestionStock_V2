package com.jumpy.tech.gestionstock.gestiondestock.conditionnement;

import com.jumpy.tech.gestionstock.gestiondestock.dto.ConditionnementDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Conditionnement;
import com.jumpy.tech.gestionstock.gestiondestock.entities.UniteMesure;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ConditionnementRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ce qu'une ligne de vente ou de commande devient en stock.
 *
 * Une ligne garde ce qui a ete saisi — trois cartons a tel prix —, et c'est ici qu'on la traduit
 * en unites de base pour le stock. Les montants ne sont donc jamais convertis : un carton vendu
 * 9 000 se facture 9 000, sans passer par un prix unitaire arrondi.
 */
@Component
public class Conditionnements {

    /** Ce que la ligne fait du conditionnement : le vendre ou l'acheter. */
    public enum Usage { VENTE, ACHAT }

    private final ConditionnementRepository conditionnementRepository;

    public Conditionnements(ConditionnementRepository conditionnementRepository) {
        this.conditionnementRepository = conditionnementRepository;
    }

    /**
     * Le conditionnement demande par une ligne, verifie ; nul si la ligne est a l'unite de base.
     *
     * Seul l'identifiant de la demande est lu : la contenance et le prix d'un carton ne se
     * declarent pas dans une requete, sans quoi un « carton de 1 » au prix d'un carton viderait
     * le stock a contre-sens.
     */
    public Conditionnement pourLigne(Article article, ConditionnementDto demande, Usage usage) {
        if (demande == null || demande.getId() == null) {
            return null;
        }
        Conditionnement conditionnement = conditionnementRepository.findById(demande.getId())
                .filter(c -> c.getArticle() != null && c.getArticle().getId().equals(article.getId()))
                .orElseThrow(() -> new InvalidEntityException(
                        "Le conditionnement " + demande.getId() + " n'est pas un conditionnement de l'article "
                                + article.getCodeArticle(),
                        ErrorCodes.CONDITIONNEMENT_NOT_VALID));
        if (!conditionnement.isActif()) {
            throw new InvalidEntityException(
                    "Le conditionnement « " + conditionnement.getLibelle() + " » a été retiré",
                    ErrorCodes.CONDITIONNEMENT_NOT_VALID);
        }
        if (usage == Usage.VENTE && !conditionnement.isVendable()) {
            throw new InvalidEntityException(
                    "« " + conditionnement.getLibelle() + " » ne se vend pas", ErrorCodes.CONDITIONNEMENT_NOT_VALID);
        }
        if (usage == Usage.ACHAT && !conditionnement.isAchetable()) {
            throw new InvalidEntityException(
                    "« " + conditionnement.getLibelle() + " » ne s'achète pas", ErrorCodes.CONDITIONNEMENT_NOT_VALID);
        }
        return conditionnement;
    }

    /**
     * Le conditionnement d'une vente qui a deja eu lieu, sur un poste hors ligne.
     *
     * Seule l'appartenance a l'article se verifie : le carton a pu etre retire entre la vente et
     * son envoi, mais il a bien ete vendu. Refuser laisserait la vente en file sur le poste pour
     * toujours.
     */
    public Conditionnement pourLigneConstatee(Article article, ConditionnementDto demande) {
        if (demande == null || demande.getId() == null) {
            return null;
        }
        return conditionnementRepository.findById(demande.getId())
                .filter(c -> c.getArticle() != null && c.getArticle().getId().equals(article.getId()))
                .orElseThrow(() -> new InvalidEntityException(
                        "Le conditionnement " + demande.getId() + " n'est pas un conditionnement de l'article "
                                + article.getCodeArticle(),
                        ErrorCodes.CONDITIONNEMENT_NOT_VALID));
    }

    /** Combien d'unites de base vaut une unite de la ligne. */
    public static BigDecimal contenance(Conditionnement conditionnement) {
        return conditionnement == null ? BigDecimal.ONE : conditionnement.getQuantiteUnites();
    }

    /** Le prix catalogue d'une unite de la ligne : celui du conditionnement, ou celui de l'article. */
    public static BigDecimal prixCatalogue(Article article, Conditionnement conditionnement) {
        return conditionnement == null ? article.getPrixUnitaire() : conditionnement.getPrixVenteHt();
    }

    /** La quantite de stock que represente une ligne. */
    public static BigDecimal enUnitesDeBase(BigDecimal quantite, BigDecimal contenance) {
        return quantite.multiply(contenance == null ? BigDecimal.ONE : contenance);
    }

    /**
     * Refuse une quantite qui n'a pas de sens : une demi-bouteille, un tiers de carton.
     *
     * Un conditionnement ne se fractionne jamais — on ouvre le carton et l'on vend des
     * bouteilles. L'unite de base, elle, se fractionne selon ce qu'elle est : le kilo oui, la
     * piece non.
     */
    public static void verifierFraction(Article article, Conditionnement conditionnement, BigDecimal quantite,
                                        ErrorCodes code) {
        if (quantite == null || estEntier(quantite)) {
            return;
        }
        if (conditionnement != null) {
            throw new InvalidEntityException(
                    "« " + conditionnement.getLibelle() + " » se compte en nombre entier : " + quantite.stripTrailingZeros().toPlainString(),
                    code, List.of("Vendez l'excédent à l'unité"));
        }
        UniteMesure unite = article.getUniteBase() == null ? UniteMesure.PIECE : article.getUniteBase();
        if (!unite.isFractionnable()) {
            throw new InvalidEntityException(
                    "L'article " + article.getCodeArticle() + " se compte à la pièce : "
                            + quantite.stripTrailingZeros().toPlainString(),
                    code, List.of("Changez l'unité de l'article si elle se vend au poids ou à la mesure"));
        }
    }

    private static boolean estEntier(BigDecimal quantite) {
        return quantite.signum() == 0 || quantite.stripTrailingZeros().scale() <= 0;
    }
}
