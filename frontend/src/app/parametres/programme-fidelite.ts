import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { messageDErreur } from '../noyau/erreurs';
import { PolitiqueFidelite, PolitiqueFideliteDto } from './politique-fidelite.service';
import { EnTetePage, Statut } from '../design';

/** Les achats d'un client imaginaire, pour la simulation : un mois de courses ordinaire. */
const DEPENSE_EXEMPLE = 100_000;

/**
 * Le programme de fidelite du magasin : ce que rapporte un achat, ce que valent les points.
 *
 * Quatre nombres, et aucun ne dit seul ce qu'il coute. Ce qui compte pour le gerant, c'est ce
 * qu'il rend a ses clients — la part de leurs achats qui leur revient en bons. C'est donc le
 * chiffre dominant de l'ecran, recalcule a chaque frappe, avec un client imaginaire pour le
 * rendre concret.
 */
@Component({
  selector: 'app-programme-fidelite',
  imports: [
    EnTetePage,
    Statut,
    DecimalPipe,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSlideToggleModule,
  ],
  templateUrl: './programme-fidelite.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProgrammeFidelite implements OnInit {
  private readonly politique = inject(PolitiqueFidelite);
  private readonly snack = inject(MatSnackBar);

  protected readonly chargement = signal(true);
  protected readonly envoi = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly brouillon = signal<PolitiqueFideliteDto>({});

  protected readonly depenseExemple = DEPENSE_EXEMPLE;

  private readonly montantParPoint = computed(() => Number(this.brouillon().montantParPoint) || 0);
  private readonly valeurPoint = computed(() => Number(this.brouillon().valeurPointFcfa) || 0);
  private readonly minimum = computed(() => Number(this.brouillon().pointsMinimumBon) || 0);

  /** La part des achats rendue en bons, en pour cent. */
  protected readonly tauxDeRetour = computed(() => {
    const parPoint = this.montantParPoint();
    return parPoint > 0 ? (this.valeurPoint() / parPoint) * 100 : 0;
  });

  /** Ce que gagne le client imaginaire. Un point par tranche entiere, comme au comptoir. */
  protected readonly pointsExemple = computed(() => {
    const parPoint = this.montantParPoint();
    return parPoint > 0 ? Math.floor(DEPENSE_EXEMPLE / parPoint) : 0;
  });

  protected readonly valeurExemple = computed(() =>
    Math.floor(this.pointsExemple() * this.valeurPoint()),
  );

  /** Ce qu'il faut avoir depense pour pouvoir demander un premier bon. */
  protected readonly depensePourUnBon = computed(() => this.minimum() * this.montantParPoint());

  /** Le plus petit bon possible. */
  protected readonly plusPetitBon = computed(() => Math.floor(this.minimum() * this.valeurPoint()));

  /** Les reglages tels que le serveur les acceptera : on ne laisse pas partir ce qu'il refusera. */
  protected readonly valide = computed(
    () =>
      this.montantParPoint() > 0 &&
      this.valeurPoint() > 0 &&
      this.minimum() >= 1 &&
      Number(this.brouillon().dureeValiditeBonJours) >= 1,
  );

  ngOnInit(): void {
    this.politique.lire().subscribe({
      next: (politique) => {
        this.brouillon.set({ ...politique });
        this.chargement.set(false);
      },
      error: (echec: unknown) => {
        this.erreur.set(messageDErreur(echec, 'Le programme de fidélité n’a pas pu être lu.'));
        this.chargement.set(false);
      },
    });
  }

  protected changer<C extends keyof PolitiqueFideliteDto>(
    champ: C,
    valeur: PolitiqueFideliteDto[C],
  ): void {
    this.brouillon.update((politique) => ({ ...politique, [champ]: valeur }));
  }

  protected enregistrer(): void {
    if (this.envoi() || !this.valide()) {
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    this.politique.enregistrer(this.brouillon()).subscribe({
      next: (enregistree) => {
        this.brouillon.set({ ...enregistree });
        this.envoi.set(false);
        this.snack.open(
          'Programme enregistré — il vaut pour les prochains tickets et les prochains bons.',
          'Fermer',
          { duration: 4000 },
        );
      },
      error: (echec: unknown) => {
        this.envoi.set(false);
        this.erreur.set(messageDErreur(echec, 'Le programme de fidélité n’a pas pu être enregistré.'));
      },
    });
  }
}
