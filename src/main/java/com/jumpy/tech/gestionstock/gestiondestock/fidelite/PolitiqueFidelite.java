package com.jumpy.tech.gestionstock.gestiondestock.fidelite;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.PolitiqueFideliteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Entreprise;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EntrepriseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * La politique de fidelite du magasin de l'appelant, lue et reglee par son gerant.
 *
 * Elle vivait dans l'identite du magasin, que seul l'administrateur peut ecrire. Or c'est une
 * decision commerciale, au meme titre qu'un prix : elle revient au gerant.
 *
 * L'entreprise vient du compte connecte, jamais de la requete — comme partout.
 */
@Service
public class PolitiqueFidelite {

    /** Dix ans : au-dela, ce n'est plus une validite, c'est une faute de frappe. */
    static final int DUREE_VALIDITE_MAX_JOURS = 3650;

    private final EntrepriseRepository entreprises;
    private final Cloisonnement cloisonnement;

    public PolitiqueFidelite(EntrepriseRepository entreprises, Cloisonnement cloisonnement) {
        this.entreprises = entreprises;
        this.cloisonnement = cloisonnement;
    }

    @Transactional(readOnly = true)
    public PolitiqueFideliteDto lire() {
        return PolitiqueFideliteDto.de(mienne());
    }

    /**
     * Enregistre la politique. Un champ absent ne change pas : un ecran qui n'en connait qu'une
     * partie ne remet pas le reste a sa valeur par defaut.
     *
     * Elle vaut pour l'avenir. Les points deja gagnes gardent leur nombre et prennent la valeur du
     * jour ou on les echange ; un bon deja emis garde son montant et son echeance.
     */
    @Transactional
    public PolitiqueFideliteDto regler(PolitiqueFideliteDto politique) {
        List<String> erreurs = erreurs(politique);
        if (!erreurs.isEmpty()) {
            throw new InvalidEntityException("La politique de fidélité n'est pas valide",
                    ErrorCodes.FIDELITE_NOT_VALID, erreurs);
        }
        Entreprise entreprise = mienne();
        if (politique.getFideliteActive() != null) {
            entreprise.setFideliteActive(politique.getFideliteActive());
        }
        if (politique.getMontantParPoint() != null) {
            entreprise.setMontantParPoint(politique.getMontantParPoint());
        }
        if (politique.getValeurPointFcfa() != null) {
            entreprise.setValeurPointFcfa(politique.getValeurPointFcfa());
        }
        if (politique.getPointsMinimumBon() != null) {
            entreprise.setPointsMinimumBon(politique.getPointsMinimumBon());
        }
        if (politique.getDureeValiditeBonJours() != null) {
            entreprise.setDureeValiditeBonJours(politique.getDureeValiditeBonJours());
        }
        return PolitiqueFideliteDto.de(entreprises.save(entreprise));
    }

    static List<String> erreurs(PolitiqueFideliteDto politique) {
        List<String> erreurs = new ArrayList<>();
        if (politique == null) {
            erreurs.add("La politique est vide");
            return erreurs;
        }
        if (politique.getMontantParPoint() != null && politique.getMontantParPoint().signum() <= 0) {
            erreurs.add("Le montant d'achat pour un point doit être positif");
        }
        if (politique.getValeurPointFcfa() != null && politique.getValeurPointFcfa().signum() <= 0) {
            erreurs.add("La valeur d'un point doit être positive");
        }
        if (politique.getPointsMinimumBon() != null && politique.getPointsMinimumBon() < 1) {
            erreurs.add("Le minimum de points pour un bon doit être d'au moins 1");
        }
        if (politique.getDureeValiditeBonJours() != null
                && (politique.getDureeValiditeBonJours() < 1
                    || politique.getDureeValiditeBonJours() > DUREE_VALIDITE_MAX_JOURS)) {
            erreurs.add("La validité d'un bon doit être comprise entre 1 et " + DUREE_VALIDITE_MAX_JOURS + " jours");
        }
        return erreurs;
    }

    /**
     * Le montant d'un bon pour tant de points, arrondi au franc inferieur : le client ne recoit
     * jamais plus que ses points ne valent.
     */
    public static BigDecimal montantDuBon(int points, BigDecimal valeurPointFcfa) {
        return valeurPointFcfa.multiply(BigDecimal.valueOf(points)).setScale(0, RoundingMode.DOWN);
    }

    private Entreprise mienne() {
        Long id = cloisonnement.entrepriseCourante();
        if (id == null) {
            throw new EntityNotFoundException("Ce compte n'est rattaché à aucune entreprise",
                    ErrorCodes.ENTREPRISE_NOT_FOUND);
        }
        return entreprises.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Aucune entreprise avec l'identifiant " + id,
                        ErrorCodes.ENTREPRISE_NOT_FOUND));
    }
}
