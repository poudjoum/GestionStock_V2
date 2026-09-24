package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.CommerceDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ResumePlateformeDto;

import java.time.LocalDate;
import java.util.List;

/**
 * La plateforme : ce qui se voit et se decide au-dessus des entreprises.
 *
 * L'application savait tenir un magasin ; elle ne savait pas qu'elle en hebergeait plusieurs.
 * L'editeur n'avait aucun moyen de voir ses clients, de savoir lesquels se servent de l'outil, ni
 * de fermer l'acces a celui qui ne paie plus.
 *
 * Tout ce qui est ici est reserve au super-administrateur, et c'est le seul service du projet qui
 * lise volontairement par-dessus le cloisonnement : c'est son objet meme.
 */
public interface PlateformeService {

    /** Les commerces et leur activite, le plus recemment inscrit d'abord. */
    List<CommerceDto> commerces();

    /** Les quelques nombres qui tiennent en haut du tableau de bord. */
    ResumePlateformeDto resume();

    /**
     * Ferme l'acces d'un commerce, quelle que soit son echeance.
     *
     * Les jetons de rafraichissement de ses comptes sont revoques : sans cela, un poste deja
     * connecte continuerait a s'en fabriquer de nouveaux pendant un mois, et la suspension ne
     * suspendrait rien.
     */
    CommerceDto suspendre(Long idEntreprise);

    /** Rouvre l'acces. Le magasin retrouve ses ventes, son stock et ses comptes intacts. */
    CommerceDto reprendre(Long idEntreprise);

    /**
     * Reporte l'echeance d'un an.
     *
     * A partir de l'echeance en cours si elle est devant nous — un renouvellement anticipe ne fait
     * pas perdre les mois payes — et a partir d'aujourd'hui si elle est passee, faute de quoi un
     * commerce en retard de six mois paierait pour six mois deja ecoules.
     */
    CommerceDto renouveler(Long idEntreprise);

    /** Fixe l'echeance a une date choisie. Nulle, le commerce cesse d'etre soumis a l'abonnement. */
    CommerceDto fixerEcheance(Long idEntreprise, LocalDate echeance);
}
