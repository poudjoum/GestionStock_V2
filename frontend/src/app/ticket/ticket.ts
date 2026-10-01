import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { libelleDuMode } from '../noyau/reglements';
import { dessinerQr } from '../noyau/qr';
import { adresseDuTicket, codeLisible } from '../noyau/code-ticket';
import type { EntrepriseDto } from '../noyau/api';
import type { FactureDto } from '../comptoir/comptoir.service';

/** Ce qui a ete encaisse, quand le ticket sort du comptoir juste apres la vente. */
export interface PaiementDuTicket {
  mode: string;
  /** Ce que le client a tendu. Egal au total pour tout ce qui n'est pas des especes. */
  recu: number;
  /** Ce qu'on lui rend. Zero ailleurs que pour les especes. */
  monnaie: number;
  /** Le bon d'achat donne en paiement, s'il y en a un : il vient en deduction avant le reste. */
  bon?: { code: string; montant: number };
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

  /**
   * Un ticket sorti hors ligne le dit.
   *
   * La facture n'existe pas encore : elle sera emise quand la vente parviendra au serveur, et
   * c'est elle qui portera le numero. Le ticket donne au client montre donc les montants calcules
   * au comptoir, sans numero, et annonce que la facture suivra — plutot qu'un numero invente
   * qu'aucune facture ne porterait jamais.
   */
  readonly provisoire = input(false);
  /**
   * Si l'achat a lieu pendant une campagne du magasin. Les points ne s'annoncent qu'alors : hors
   * campagne, le client qui scannerait le ticket serait refuse. Faux par defaut — un duplicata
   * ne sait pas si sa vente en etait, et la page du QR, elle, le dit juste.
   */
  readonly enCampagne = input(false);

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

  /** Le code du ticket, lisible : trois groupes de quatre, a recopier si le QR est abime. */
  protected readonly code = computed(() => {
    const code = this.facture().codeTicket;
    return code ? codeLisible(code) : null;
  });

  /**
   * Le QR qui mene le client a son achat : le magasin, la date, les articles, les points.
   *
   * Il est toujours imprime des que le ticket a un code. Son adresse est celle que le serveur
   * declare publique quand il en a une — le client l'ouvre alors depuis n'importe ou. A defaut,
   * celle de la caisse elle-meme : elle ne mene peut-etre pas au ticket depuis le telephone d'un
   * client hors du magasin, mais le QR porte le code, et c'est le code que lit l'application de
   * fidelite pour crediter les points. Un ticket sans QR, en revanche, ne se scanne pas du tout.
   */
  protected readonly qr = computed(() => {
    const code = this.facture().codeTicket;
    const base =
      this.entreprise()?.adresseTickets || (typeof location !== 'undefined' ? location.origin : null);
    return code && base ? dessinerQr(adresseDuTicket(base, code)) : null;
  });

  /**
   * Les points que ce ticket rapporte : une tranche entiere de `montantParPoint` payee TTC. La
   * meme regle que `PointsFidelite` cote serveur, pour que le papier et la page du QR disent le
   * meme nombre.
   */
  protected readonly points = computed(() => {
    const magasin = this.entreprise();
    const facture = this.facture();
    if (!magasin || magasin.fideliteActive === false || facture.annulee || !this.enCampagne()) {
      return 0;
    }
    const parPoint = magasin.montantParPoint && magasin.montantParPoint > 0 ? magasin.montantParPoint : 10000;
    // Sur ce que le client a paye : la part reglee par bon d'achat ne rapporte rien, comme cote
    // serveur — sinon le ticket promettrait des points que le scan refuserait.
    const paye = (facture.totalTtc ?? 0) - (this.paiement()?.bon?.montant ?? 0);
    return Math.max(0, Math.floor(paye / parPoint));
  });

  protected readonly fideliteActive = computed(
    () => this.entreprise()?.fideliteActive !== false && this.enCampagne(),
  );
}
