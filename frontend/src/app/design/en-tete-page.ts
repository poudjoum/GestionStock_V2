import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * L'en-tete d'une page : son titre, une phrase qui dit ce qu'on y fait, et ses actions.
 *
 * Un seul par ecran, en haut du contenu. Les actions se projettent a droite, et passent dessous
 * quand l'ecran est etroit.
 *
 * ```html
 * <gs-en-tete-page titre="Catalogue" sousTitre="16 articles, 5 catégories">
 *   <button mat-flat-button>Nouvel article</button>
 * </gs-en-tete-page>
 * ```
 */
@Component({
  selector: 'gs-en-tete-page',
  template: `
    <div class="textes">
      <h1 class="gs-titre">{{ titre() }}</h1>
      @if (sousTitre()) {
        <p>{{ sousTitre() }}</p>
      }
    </div>
    <div class="actions"><ng-content /></div>
  `,
  host: { class: 'gs-en-tete-page' },
  styles: `
    :host {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-end;
      justify-content: space-between;
      gap: var(--gs-esp-3) var(--gs-esp-5);
      margin-bottom: var(--gs-esp-5);
    }
    .textes {
      display: grid;
      gap: 4px;
      min-width: 0;
    }
    h1 {
      margin: 0;
      font-size: var(--gs-titre-page);
      color: var(--gs-encre);
    }
    p {
      margin: 0;
      font-size: var(--gs-texte-md);
      color: var(--gs-encre-2);
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--gs-esp-2);
    }
    .actions:empty {
      display: none;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EnTetePage {
  readonly titre = input.required<string>();
  readonly sousTitre = input<string | null>(null);
}
