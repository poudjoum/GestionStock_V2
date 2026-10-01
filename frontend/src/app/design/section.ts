import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Un bloc de page : une carte, son titre et une phrase qui dit a quoi il sert.
 *
 * Le motif de tous les formulaires et de tous les panneaux : « Gagner des points », « Le
 * magasin », « Ce qui manque ». Une seule facon de l'ecrire, et l'espace entre les blocs se regle
 * par le conteneur, pas par chaque bloc.
 *
 * ```html
 * <gs-section titre="Mentions légales" description="Elles s’impriment sur chaque ticket.">
 *   <mat-form-field>…</mat-form-field>
 * </gs-section>
 * ```
 */
@Component({
  selector: 'gs-section',
  template: `
    @if (titre()) {
      <header>
        <h2>{{ titre() }}</h2>
        @if (description()) {
          <p>{{ description() }}</p>
        }
        <div class="actions"><ng-content select="[gs-section-action]" /></div>
      </header>
    }
    <ng-content />
  `,
  host: { class: 'gs-section' },
  styles: `
    :host {
      display: block;
      padding: var(--gs-esp-5);
      background: var(--gs-surface);
      border: 1px solid var(--gs-trait);
      border-radius: var(--gs-r-carte);
      min-width: 0;
    }
    header {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 2px var(--gs-esp-3);
      margin-bottom: var(--gs-esp-4);
    }
    h2 {
      grid-column: 1;
      margin: 0;
      font: 600 var(--gs-texte-lg) / 1.3 var(--gs-police-texte);
      color: var(--gs-encre);
    }
    p {
      grid-column: 1;
      margin: 0;
      font-size: var(--gs-texte-md);
      color: var(--gs-encre-2);
    }
    .actions {
      grid-column: 2;
      grid-row: 1 / span 2;
      align-self: start;
    }
    .actions:empty {
      display: none;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Section {
  readonly titre = input<string | null>(null);
  readonly description = input<string | null>(null);
}
