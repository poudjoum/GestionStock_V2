import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/**
 * Ce que dit un statut : bien, a surveiller, bloquant, promotion, ou neutre.
 *
 * Le ton est le sens, pas la couleur : `danger` pour ce qui bloque (rupture, impaye), `alerte`
 * pour ce qui approche (sous le seuil, partiel). Avoir des factures a encaisser n'est pas un
 * danger, c'est le quotidien d'un commerce.
 */
export type TonStatut = 'ok' | 'alerte' | 'danger' | 'promo' | 'neutre';

/**
 * La pastille de statut : une icone et un mot, sur un fond doux.
 *
 * Toujours les deux : une pastille qui ne dit son sens que par la couleur est illisible pour un
 * daltonien, et sur une impression en noir et blanc.
 *
 * ```html
 * <gs-statut ton="alerte" icone="trending_down">Sous le seuil</gs-statut>
 * ```
 */
@Component({
  selector: 'gs-statut',
  imports: [MatIconModule],
  template: `
    @if (icone()) {
      <mat-icon aria-hidden="true">{{ icone() }}</mat-icon>
    }
    <ng-content />
  `,
  host: {
    '[class]': '"gs-statut gs-statut--" + ton()',
  },
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 3px 8px 3px 6px;
      border-radius: 6px;
      font: 600 var(--gs-texte-xs) / 1.25 var(--gs-police-texte);
      white-space: nowrap;
      vertical-align: middle;
    }
    mat-icon {
      width: 14px;
      height: 14px;
      font-size: 14px;
      line-height: 14px;
    }
    :host(.gs-statut--ok) {
      background: var(--gs-succes-fond);
      color: var(--gs-succes);
    }
    :host(.gs-statut--alerte) {
      background: var(--gs-alerte-fond);
      color: var(--gs-alerte);
    }
    :host(.gs-statut--danger) {
      background: var(--gs-danger-fond);
      color: var(--gs-danger);
    }
    :host(.gs-statut--promo) {
      background: var(--gs-petrole-100);
      color: var(--gs-petrole-700);
    }
    :host(.gs-statut--neutre) {
      background: var(--gs-surface-2);
      color: var(--gs-encre-2);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Statut {
  readonly ton = input<TonStatut>('neutre');
  /** Le nom d'une icone Material. Facultatif, mais une pastille sans icone devrait etre rare. */
  readonly icone = input<string | null>(null);
}
