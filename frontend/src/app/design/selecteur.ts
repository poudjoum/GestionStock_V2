import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  input,
  model,
  viewChildren,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';

/** Une option du selecteur : sa valeur, ce qu'on lit, et combien d'elements elle contient. */
export interface OptionSelecteur<T> {
  valeur: T;
  libelle: string;
  /**
   * Le nombre d'elements derriere l'option. Affiche, il evite d'ouvrir un onglet vide : c'est
   * ainsi que les Achats ouvraient « À recevoir » sans rien a recevoir, alors que deux commandes
   * attendaient dans l'onglet d'a cote.
   */
  compteur?: number | null;
}

/**
 * Le selecteur a onglets : choisir une option parmi deux a cinq.
 *
 * « Aujourd'hui · 7 jours · 30 jours », « Clients · Fournisseurs », « % · Prix » : il etait
 * reecrit a la main sur chaque ecran, avec des tailles et des rayons qui variaient. Celui-ci est
 * le seul.
 *
 * Accessible comme un groupe de boutons radio : une seule tabulation pour y entrer, les fleches
 * pour changer d'option, et le lecteur d'ecran annonce laquelle est choisie.
 *
 * ```html
 * <gs-selecteur aria-label="Période" [options]="periodes" [(valeur)]="periode" />
 * ```
 */
@Component({
  selector: 'gs-selecteur',
  imports: [DecimalPipe],
  template: `
    @for (option of options(); track option.valeur; let i = $index) {
      <button
        #bouton
        type="button"
        role="radio"
        class="gs-focus"
        [attr.aria-checked]="option.valeur === valeur()"
        [tabIndex]="option.valeur === valeur() ? 0 : -1"
        (click)="choisir(option.valeur)"
        (keydown)="clavier($event, i)"
      >
        {{ option.libelle }}
        @if (option.compteur !== undefined && option.compteur !== null) {
          <span class="compteur">{{ option.compteur | number }}</span>
        }
      </button>
    }
  `,
  host: {
    role: 'radiogroup',
    class: 'gs-selecteur',
  },
  styles: `
    :host {
      display: inline-flex;
      flex-wrap: wrap;
      gap: 2px;
      padding: 3px;
      background: var(--gs-surface-2);
      border-radius: 10px;
      max-width: 100%;
    }
    button {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      min-height: 36px;
      padding: 0 12px;
      border: 0;
      border-radius: 8px;
      background: transparent;
      color: var(--gs-encre-2);
      font: 500 var(--gs-texte-sm) / 1 var(--gs-police-texte);
      cursor: pointer;
      white-space: nowrap;
    }
    button:hover {
      color: var(--gs-encre);
    }
    button[aria-checked='true'] {
      background: var(--gs-surface);
      color: var(--gs-encre);
      font-weight: 600;
      box-shadow: var(--gs-ombre-1);
    }
    .compteur {
      min-width: 20px;
      padding: 2px 6px;
      border-radius: var(--gs-r-pilule);
      background: var(--gs-trait);
      color: var(--gs-encre-2);
      font-size: var(--gs-texte-xs);
      font-weight: 600;
      font-variant-numeric: tabular-nums;
      text-align: center;
    }
    button[aria-checked='true'] .compteur {
      background: var(--gs-accent-doux);
      color: var(--gs-sur-accent-doux);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Selecteur<T> {
  readonly options = input.required<OptionSelecteur<T>[]>();
  readonly valeur = model.required<T>();

  private readonly boutons = viewChildren<ElementRef<HTMLButtonElement>>('bouton');

  protected choisir(valeur: T): void {
    this.valeur.set(valeur);
  }

  /** Les fleches parcourent les options, comme dans un groupe de boutons radio. */
  protected clavier(evenement: KeyboardEvent, index: number): void {
    const options = this.options();
    const pas =
      evenement.key === 'ArrowRight' || evenement.key === 'ArrowDown'
        ? 1
        : evenement.key === 'ArrowLeft' || evenement.key === 'ArrowUp'
          ? -1
          : 0;
    let cible: number | null = null;
    if (pas !== 0) {
      cible = (index + pas + options.length) % options.length;
    } else if (evenement.key === 'Home') {
      cible = 0;
    } else if (evenement.key === 'End') {
      cible = options.length - 1;
    }
    if (cible === null) {
      return;
    }
    evenement.preventDefault();
    this.choisir(options[cible].valeur);
    this.boutons()[cible]?.nativeElement.focus();
  }
}
