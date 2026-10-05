import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { LotDto, Lots, RappelLotDto, statutDuLot } from './lots.service';
import { Stock } from './stock.service';
import { Session } from '../noyau/session';
import { Sites } from '../noyau/sites';
import { messageDErreur } from '../noyau/erreurs';
import { EnTetePage, EtatVide, OptionSelecteur, Section, Selecteur, Statut, Tuile } from '../design';

type Filtre = 'tout' | 'perimes' | 'bientot';

/**
 * Les peremptions : ce qui a perime et reste en rayon, ce qui va perimer.
 *
 * Deux gestes, pas plus. Le magasinier passe dans les rayons, retire le lot perime et le declare
 * d'un bouton — la quantite est deja la, c'est celle du lot. Le gerant, quand un fournisseur
 * rappelle un lot, retrouve ou il en reste et a qui il a ete vendu.
 *
 * Le lot le plus urgent est en tete : la liste se lit de haut en bas comme une tournee.
 */
@Component({
  selector: 'app-peremptions',
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    EnTetePage,
    EtatVide,
    Section,
    Selecteur,
    Statut,
    Tuile,
  ],
  templateUrl: './peremptions.html',
  styles: `
    .tuiles {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: var(--gs-esp-3);
      margin-bottom: var(--gs-esp-4);
    }

    .lot {
      display: block;
      font-family: var(--gs-police-code, monospace);
      font-size: var(--gs-texte-sm);
    }

    .volet-fond {
      position: fixed;
      inset: 0;
      z-index: 20;
      background: rgb(0 0 0 / 0.32);
    }

    .volet {
      position: fixed;
      z-index: 21;
      inset: auto 0 0 0;
      max-height: 90dvh;
      overflow-y: auto;
      padding: 16px;
      border-radius: 16px 16px 0 0;
      background: var(--gs-surface);
      box-shadow: var(--gs-ombre-2);
    }

    @media (min-width: 768px) {
      .volet {
        inset: 0 0 0 auto;
        width: 440px;
        max-height: none;
        border-radius: 0;
      }
    }

    .ventes {
      display: grid;
      gap: var(--gs-esp-2);
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .ventes > li {
      padding: 10px 12px;
      border: 1px solid var(--gs-trait);
      border-radius: var(--gs-r-champ);
    }
  `,
})
export class Peremptions implements OnInit {
  private readonly lots = inject(Lots);
  private readonly stock = inject(Stock);
  private readonly session = inject(Session);
  private readonly snack = inject(MatSnackBar);
  private readonly sites = inject(Sites);

  protected readonly siteActif = this.sites.actif;
  protected readonly peutVoirTout = computed(() => this.sites.tousLesSites() && this.sites.plusieurs());
  protected readonly tousSites = signal(false);
  protected readonly portees = computed<OptionSelecteur<'site' | 'tous'>[]>(() => [
    { valeur: 'site', libelle: this.siteActif()?.nom ?? 'Ce site' },
    { valeur: 'tous', libelle: 'Tous les sites' },
  ]);

  protected readonly portee = computed<'site' | 'tous'>(() => (this.tousSites() ? 'tous' : 'site'));

  protected readonly lignes = signal<LotDto[]>([]);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);
  protected readonly filtre = signal<Filtre>('tout');

  protected readonly perimes = computed(() => this.lignes().filter((l) => l.etat === 'PERIME'));
  protected readonly bientot = computed(() => this.lignes().filter((l) => l.etat === 'BIENTOT'));
  /** Une DLC depassee ne se vend plus : c'est elle qui bloque, et qu'il faut retirer d'abord. */
  protected readonly dlcDepassees = computed(() => this.perimes().filter((l) => l.typeDate === 'DLC').length);
  protected readonly affichees = computed(() => {
    switch (this.filtre()) {
      case 'perimes':
        return this.perimes();
      case 'bientot':
        return this.bientot();
      default:
        return this.lignes();
    }
  });
  protected readonly filtres = computed<OptionSelecteur<Filtre>[]>(() => [
    { valeur: 'tout', libelle: `Tout (${this.lignes().length})` },
    { valeur: 'perimes', libelle: `Dépassés (${this.perimes().length})` },
    { valeur: 'bientot', libelle: `Bientôt (${this.bientot().length})` },
  ]);

  /** Retirer du rayon : ceux qui tiennent la marchandise. */
  protected readonly peutRetirer = computed(() =>
    this.session.roles().some((r) => ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER'].includes(r)),
  );
  /** Le rappel nomme les clients : le gerant seul, et le serveur le redit. */
  protected readonly peutRappeler = computed(() =>
    this.session.roles().some((r) => ['ROLE_ADMIN', 'ROLE_MANAGER'].includes(r)),
  );

  /** Le lot qu'on retire du rayon ; nul, le volet est ferme. */
  protected readonly retrait = signal<LotDto | null>(null);
  protected readonly quantiteRetiree = signal<number | null>(null);
  protected readonly envoi = signal(false);
  protected readonly erreurRetrait = signal<string | null>(null);

  protected readonly rappel = signal<RappelLotDto | null>(null);
  protected readonly chargementRappel = signal(false);
  protected readonly erreurRappel = signal<string | null>(null);

  protected readonly statut = statutDuLot;

  ngOnInit(): void {
    this.charger();
  }

  protected changerPortee(portee: 'site' | 'tous'): void {
    this.tousSites.set(portee === 'tous');
    this.charger();
  }

  protected ouvrirRetrait(lot: LotDto): void {
    this.rappel.set(null);
    this.retrait.set(lot);
    this.quantiteRetiree.set(lot.quantite ?? null);
    this.erreurRetrait.set(null);
  }

  protected retirer(): void {
    const lot = this.retrait();
    const quantite = this.quantiteRetiree();
    if (!lot || !quantite || quantite <= 0 || this.envoi()) {
      return;
    }
    this.envoi.set(true);
    this.erreurRetrait.set(null);
    this.stock
      .ajuster('sortie', {
        article: { id: lot.idArticle },
        quantite,
        motif: 'PEREMPTION',
        idLot: lot.id,
        idSite: lot.idSite,
      })
      .subscribe({
        next: () => {
          this.envoi.set(false);
          this.retrait.set(null);
          this.snack.open(`Lot ${lot.numero} retiré du stock : ${lot.designation}.`, 'Fermer', { duration: 4000 });
          this.charger();
        },
        error: (echec: unknown) => {
          this.envoi.set(false);
          this.erreurRetrait.set(messageDErreur(echec, 'Le retrait n’a pas pu être enregistré.'));
        },
      });
  }

  protected ouvrirRappel(lot: LotDto): void {
    this.retrait.set(null);
    this.chargementRappel.set(true);
    this.erreurRappel.set(null);
    this.rappel.set({ lot, stocks: [], ventes: [] });
    this.lots.rappel(lot.id!).subscribe({
      next: (rappel) => {
        this.rappel.set(rappel);
        this.chargementRappel.set(false);
      },
      error: (echec: unknown) => {
        this.chargementRappel.set(false);
        this.erreurRappel.set(messageDErreur(echec, 'Le rappel n’a pas pu être chargé.'));
      },
    });
  }

  protected fermer(): void {
    this.retrait.set(null);
    this.rappel.set(null);
  }

  private charger(): void {
    this.chargement.set(true);
    this.erreur.set(null);
    this.lots.peremption(this.tousSites()).subscribe({
      next: (lignes) => {
        this.lignes.set(lignes);
        this.chargement.set(false);
        // Un filtre qui ne montre plus rien apres un retrait ramene a la liste entiere.
        if (this.affichees().length === 0) {
          this.filtre.set('tout');
        }
      },
      error: (echec: unknown) => {
        this.chargement.set(false);
        this.erreur.set(messageDErreur(echec, 'Les péremptions n’ont pas pu être chargées.'));
      },
    });
  }
}
