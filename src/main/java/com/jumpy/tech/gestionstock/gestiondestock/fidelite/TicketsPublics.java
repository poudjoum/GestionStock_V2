package com.jumpy.tech.gestionstock.gestiondestock.fidelite;

import com.jumpy.tech.gestionstock.gestiondestock.dto.TicketPublicDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Entreprise;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Facture;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Vente;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.promotion.Calendrier;
import com.jumpy.tech.gestionstock.gestiondestock.repository.CampagneRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EntrepriseRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.FactureRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneFactureRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneVenteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ReglementRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.VenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Le ticket retrouve par le code de son QR.
 *
 * Cette lecture traverse le cloisonnement, et c'est voulu : le client n'a pas de compte, donc pas
 * d'entreprise. Ce qui la protege, c'est le code — soixante bits tires au hasard : le connaitre,
 * c'est avoir le papier en main —, et ce qu'elle rend : rien que le papier ne disait deja.
 */
@Service
public class TicketsPublics {

    private final VenteRepository ventes;
    private final FactureRepository factures;
    private final LigneFactureRepository lignesFacture;
    private final LigneVenteRepository lignesVente;
    private final EntrepriseRepository entreprises;
    private final CampagneRepository campagnes;
    private final ReglementRepository reglements;
    private final Calendrier calendrier;

    public TicketsPublics(VenteRepository ventes, FactureRepository factures,
                          LigneFactureRepository lignesFacture, LigneVenteRepository lignesVente,
                          EntrepriseRepository entreprises, CampagneRepository campagnes,
                          Calendrier calendrier, ReglementRepository reglements) {
        this.reglements = reglements;
        this.campagnes = campagnes;
        this.calendrier = calendrier;
        this.ventes = ventes;
        this.factures = factures;
        this.lignesFacture = lignesFacture;
        this.lignesVente = lignesVente;
        this.entreprises = entreprises;
    }

    @Transactional(readOnly = true)
    public TicketPublicDto parCode(String saisi) {
        // Un code mal forme et un code inconnu rendent la meme reponse : distinguer les deux
        // apprendrait a qui essaie des codes lesquels ont la bonne forme.
        Vente vente = CodeTicket.lire(saisi)
                .flatMap(ventes::findByCodeTicket)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun ticket ne porte ce code. S'il a été imprimé hors ligne, il apparaîtra "
                                + "dès que la caisse aura retrouvé le réseau.",
                        ErrorCodes.VENTE_NOT_FOUND));

        Entreprise entreprise = vente.getIdEntreprise() == null ? null
                : entreprises.findById(vente.getIdEntreprise()).orElse(null);
        // Un ticket ne rapporte des points que s'il a ete achete pendant une campagne.
        boolean fideliteActive = entreprise != null && entreprise.isFideliteActive()
                && campagnes.achatEnCampagne(entreprise.getId(), calendrier.jourDe(vente.getDatevente()));
        BigDecimal montantParPoint = entreprise == null ? null : entreprise.getMontantParPoint();

        Facture facture = factures.findByVenteId(vente.getId()).orElse(null);
        List<TicketPublicDto.Ligne> articles;
        BigDecimal total = null;
        int points = 0;
        if (facture != null) {
            // Les lignes de la facture, et non celles de la vente : elles ont fige la designation,
            // le prix et la TVA au moment de l'achat. Un article renomme depuis ne change pas le
            // ticket du client.
            articles = lignesFacture.findAllByFactureId(facture.getId()).stream()
                    .map(l -> TicketPublicDto.Ligne.builder()
                            .designation(l.getDesignation())
                            .quantite(l.getQuantite())
                            .montant(l.getMontantTtc())
                            .build())
                    .toList();
            total = facture.getTotalTtc();
            boolean compte = !vente.isAnnulee() && !facture.isAnnulee();
            points = compte ? PointsFidelite.pour(total, reglements.totalRegleParBonsPour(facture.getId()),
                    fideliteActive, montantParPoint) : 0;
        } else {
            // Pas encore de facture : les articles sont connus, le total qui fait foi pas encore.
            articles = lignesVente.findAllByVenteId(vente.getId()).stream()
                    .map(l -> TicketPublicDto.Ligne.builder()
                            .designation(l.getArticles() == null ? null : l.getArticles().getDesignation())
                            .quantite(l.getQuantite())
                            .build())
                    .toList();
        }

        return TicketPublicDto.builder()
                .code(vente.getCodeTicket())
                .magasin(entreprise == null ? null : entreprise.getNom())
                .ville(entreprise == null || entreprise.getAdresse() == null ? null
                        : entreprise.getAdresse().getVille())
                .date(vente.getDatevente())
                .articles(articles)
                .totalTtc(total)
                .points(points)
                .fideliteActive(fideliteActive)
                .montantParPoint(montantParPoint)
                .annulee(vente.isAnnulee() || (facture != null && facture.isAnnulee()))
                .build();
    }
}
