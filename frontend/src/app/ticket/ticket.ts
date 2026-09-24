import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { libelleDuMode } from '../noyau/reglements';
import type { EntrepriseDto } from '../noyau/api';
import type { FactureDto } from '../comptoir/comptoir.service';

/** Ce qui a ete encaisse, quand le ticket sort du comptoir juste apres la vente. */
export interface PaiementDuTicket {
  mode: string;
  /** Ce que le client a tendu. Egal au total pour tout ce qui n'est pas des especes. */
  recu: number;
  /** Ce qu'on lui rend. Zero ailleurs que pour les especes. */
  monnaie: number;
}

/**
 * Le ticket de caisse, sur 72 mm.
 *
 * Il n'invente aucun chiffre : tout vient de la facture, qui a fige a son emission le code, la
 * designation, le prix et le taux de TVA de chaque ligne. C'est ce qui permet de le reimprimer
 * six mois plus tard, apres un changement de prix, et d'obtenir exactement le meme papier — un
 * ticket recalcule a partir du catalogue courant, lui, aurait change en silence.
 *
 * Le meme composant sert a trois endroits : le comptoir l'imprime apres l'encaissement, l'ecran
 * des factures le reimprime, et le parametrage du magasin l'affiche en apercu. Un apercu qui
 * serait une maquette separee finirait par mentir.
 */
@Component({
  selector: 'app-ticket',
  imports: [DatePipe, DecimalPipe],
  templateUrl: './ticket.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Ticket {
  readonly facture = input.required<FactureDto>();
  readonly entreprise = input<EntrepriseDto | null>(null);
  readonly paiement = input<PaiementDuTicket | null>(null);

  /**
   * Un duplicata le dit.
   *
   * Sans cette mention, un ticket reimprime est indiscernable de l'original, et deux papiers
   * identiques circulent pour un seul encaissement.
   */
  readonly duplicata = input(false);

  /** L'adresse en une ligne, sans les vides : un magasin ne renseigne pas toujours tout. */
  protected readonly adresse = computed(() => {
    const a = this.entreprise()?.adresse;
    return [a?.adresse1, a?.ville, a?.pays].filter(Boolean).join(' — ');
  });

  protected readonly lignes = computed(() => this.facture().lignes ?? []);

  /**
   * Le taux affiche a cote du total de TVA.
   *
   * Toutes les lignes portent presque toujours le meme, mais rien ne l'impose : un article peut
   * avoir le sien. Quand ils different, le ticket n'annonce pas de taux plutot qu'un taux faux.
   */
  protected readonly tauxUnique = computed(() => {
    const taux = new Set(this.lignes().map((l) => l.tauxTva ?? 0));
    return taux.size === 1 ? [...taux][0] : null;
  });

  protected readonly modeLisible = computed(() => {
    const mode = this.paiement()?.mode;
    return mode ? libelleDuMode(mode) : '';
  });

  /** Vrai quand le client repart sans rien devoir — ce que le ticket doit dire clairement. */
  protected readonly solde = computed(() => (this.facture().resteAPayer ?? 0) <= 0);
}
