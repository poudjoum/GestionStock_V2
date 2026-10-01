import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';

/**
 * La tuile chiffre : ce qu'on vient chercher sur un tableau de bord, en gros.
 *
 * Quand la donnee manque, la tuile le dit en grand — « Coûts à saisir » — au lieu d'afficher un
 * zero. Un « 0 F » de valeur de stock se lit comme un stock qui ne vaut rien, alors qu'il dit
 * seulement que personne n'a saisi les couts.
 *
 * ```html
 * <gs-tuile libelle="Caisse du jour" icone="point_of_sale" [valeur]="333739" unite="F"
 *           detail="11 règlements" />
 * <gs-tuile libelle="Valeur du stock" [valeur]="null" manquant="Coûts à saisir"
 *           detail="16 articles sans coût d’achat" />
 * ```
 *
 * Le contenu projete se place en bas : un lien « Tout voir », une action.
 */
@Component({
  selector: 'gs-tuile',
  imports: [DecimalPipe, MatIconModule],
  template: `
    <span class="libelle">
      @if (icone()) {
        <mat-icon aria-hidden="true">{{ icone() }}</mat-icon>
      }
      {{ libelle() }}
    </span>
    @if (connue()) {
      <span class="valeur">
        {{ valeur() | number: format() }}@if (unite()) {<small>{{ unite() }}</small>}
      </span>
    } @else {
      <span class="valeur valeur--manquante">{{ manquant() }}</span>
    }
    @if (detail()) {
      <span class="detail">{{ detail() }}</span>
    }
    <ng-content />
  `,
  host: {
    '[class]': '"gs-tuile gs-tuile--" + ton()',
  },
  styles: `
    :host {
      display: grid;
      align-content: start;
      gap: 6px;
      padding: 14px 16px;
      background: var(--gs-surface);
      border: 1px solid var(--gs-trait);
      border-radius: var(--gs-r-carte);
      min-width: 0;
    }
    .libelle {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: var(--gs-texte-xs);
      font-weight: 600;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      color: var(--gs-encre-3);
    }
    mat-icon {
      width: 16px;
      height: 16px;
      font-size: 16px;
    }
    .valeur {
      font: 700 var(--gs-chiffre) / 1.05 var(--gs-police-titre);
      font-variant-numeric: tabular-nums;
      letter-spacing: -0.01em;
      color: var(--gs-encre);
      overflow-wrap: anywhere;
    }
    .valeur small {
      margin-left: 4px;
      font: 500 var(--gs-texte-lg) var(--gs-police-texte);
      color: var(--gs-encre-3);
    }
    .valeur--manquante {
      font-size: 1.25rem;
      color: var(--gs-encre-2);
    }
    .detail {
      font-size: var(--gs-texte-sm);
      color: var(--gs-encre-2);
    }
    :host(.gs-tuile--attention) {
      background: var(--gs-alerte-fond);
      border-color: color-mix(in srgb, var(--gs-alerte) 40%, transparent);
    }
    :host(.gs-tuile--attention) .valeur,
    :host(.gs-tuile--attention) .libelle {
      color: var(--gs-alerte);
    }
    :host(.gs-tuile--danger) {
      background: var(--gs-danger-fond);
      border-color: color-mix(in srgb, var(--gs-danger) 40%, transparent);
    }
    :host(.gs-tuile--danger) .valeur,
    :host(.gs-tuile--danger) .libelle {
      color: var(--gs-danger);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Tuile {
  readonly libelle = input.required<string>();
  /** Nul ou absent : la donnee manque, et la tuile affiche `manquant` a la place d'un chiffre. */
  readonly valeur = input<number | null | undefined>(null);
  readonly unite = input<string | null>(null);
  readonly detail = input<string | null>(null);
  readonly icone = input<string | null>(null);
  readonly manquant = input('Non renseigné');
  /** `attention` pour ce qui demande une action bientot, `danger` pour ce qui bloque. */
  readonly ton = input<'normal' | 'attention' | 'danger'>('normal');
  /** Le format du pipe `number` : entier par defaut, la monnaie n'a pas de centimes. */
  readonly format = input('1.0-0');

  protected readonly connue = computed(() => {
    const valeur = this.valeur();
    return valeur !== null && valeur !== undefined && !Number.isNaN(valeur);
  });
}
