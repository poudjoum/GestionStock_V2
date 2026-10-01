package com.jumpy.tech.gestionstock.gestiondestock.fidelite;

import com.jumpy.tech.gestionstock.gestiondestock.entities.BonDAchat;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Facture;
import com.jumpy.tech.gestionstock.gestiondestock.entities.StatutBonDAchat;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.BonDAchatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Un bon d'achat donne en paiement.
 *
 * Il diminue le reste a payer de sa valeur, et il se consomme en entier : un bon de 2 000 F donne
 * pour un reste de 1 500 F regle les 1 500 F, et les 500 F restants sont perdus — un bon ne rend
 * pas la monnaie. La caisse le dit avant qu'on valide.
 *
 * Le bon est lu sous verrou : deux caisses qui l'encaissent en meme temps ne le font valoir qu'une
 * fois.
 */
@Service
public class PaiementParBon {

    private final BonDAchatRepository bons;

    public PaiementParBon(BonDAchatRepository bons) {
        this.bons = bons;
    }

    /** Ce qu'un bon a regle : son code, tel qu'il est range, et le montant porte sur la facture. */
    public record Paiement(String codeBon, BigDecimal montant) {
    }

    /**
     * Consomme le bon sur la facture et rend le montant a enregistrer comme reglement.
     *
     * Dans la transaction de l'appelant : si le reglement ne s'enregistre pas, le bon redevient
     * utilisable avec lui.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Paiement consommer(String codeSaisi, Facture facture, BigDecimal resteAPayer) {
        String code = CodeBonAchat.normaliser(codeSaisi)
                .orElseThrow(() -> new InvalidEntityException("Code de bon d'achat invalide : " + codeSaisi,
                        ErrorCodes.BON_ACHAT_NOT_VALID, List.of("Format attendu : BON-XXXX-XXXX")));
        BonDAchat bon = bons.verrouillerParCode(code)
                // Le bon d'un autre magasin rend la meme reponse qu'un bon inconnu, comme partout.
                .filter(b -> Objects.equals(b.getEntreprise().getId(), facture.getIdEntreprise()))
                .orElseThrow(() -> new EntityNotFoundException("Aucun bon d'achat de ce magasin ne porte le code " + code,
                        ErrorCodes.BON_ACHAT_NOT_FOUND));
        if (bon.getStatut() != StatutBonDAchat.ACTIF) {
            throw new InvalidEntityException("Ce bon d'achat a déjà été utilisé.",
                    ErrorCodes.BON_ACHAT_NOT_VALID, List.of("Bon " + code + " : " + bon.getStatut()));
        }
        if (bon.getDateExpiration().isBefore(Instant.now())) {
            bon.setStatut(StatutBonDAchat.EXPIRE);
            bons.save(bon);
            throw new InvalidEntityException("Ce bon d'achat a expiré.",
                    ErrorCodes.BON_ACHAT_NOT_VALID, List.of("Bon " + code + " expiré le " + bon.getDateExpiration()));
        }

        bon.setStatut(StatutBonDAchat.UTILISE);
        bon.setDateUtilisation(Instant.now());
        bon.setVenteUtilisation(facture.getVente());
        bons.save(bon);
        return new Paiement(code, bon.getMontantFcfa().min(resteAPayer));
    }

    /**
     * Le reglement par bon est repris : le bon redevient utilisable, comme si on ne l'avait pas
     * donne. S'il a expire entre-temps, il le restera — c'est sa date qui le dit, pas son statut.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void rendre(String code) {
        if (code == null) {
            return;
        }
        bons.verrouillerParCode(code).ifPresent(bon -> {
            bon.setStatut(StatutBonDAchat.ACTIF);
            bon.setDateUtilisation(null);
            bon.setVenteUtilisation(null);
            bons.save(bon);
        });
    }
}
