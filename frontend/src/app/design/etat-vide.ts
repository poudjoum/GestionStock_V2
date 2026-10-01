import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/**
 * Ce qu'on montre quand il n'y a rien : ce que la page contiendra, et comment y mettre la
 * premiere chose. Jamais « Aucune donnée ».
 *
 * Deux cas a ne pas confondre : la liste vide (`filtre` absent) dit quoi faire pour la remplir ;
 * la liste videe par un filtre rappelle le filtre, pour qu'on ne croie pas qu'il n'y a rien.
 *
 * ```html
 * <gs-etat-vide icone="campaign" titre="Aucune campagne pour l’instant"
 *               texte="Une campagne met des articles en promotion sur une période.">
 *   <button mat-flat-button>Créer une campagne</button>
 * </gs-etat-vide>
 * <gs-etat-vide icone="search_off" titre="Rien ne correspond à « ciment »" [filtre]="true" />
 * ```
 */
@Component({
  selector: 'gs-etat-vide',
  imports: [MatIconModule],
  template: `
    <span class="pictogramme" aria-hidden="true"><mat-icon>{{ icone() }}</mat-icon></span>
    <p class="titre">{{ titre() }}</p>
    @if (texte()) {
      <p class="texte">{{ texte() }}</p>
    }
    <div class="action"><ng-content /></div>
  `,
  host: {
    class: 'gs-etat-vide',
    '[class.gs-etat-vide--filtre]': 'filtre()',
    role: 'status',
  },
  styles: `
    :host {
      display: grid;
      justify-items: center;
      gap: var(--gs-esp-2);
      padding: var(--gs-esp-6) var(--gs-esp-4);
      text-align: center;
    }
    .pictogramme {
      display: grid;
      place-items: center;
      width: 56px;
      height: 56px;
      margin-bottom: var(--gs-esp-1);
      border-radius: 16px;
      background: var(--gs-accent-doux);
      color: var(--gs-sur-accent-doux);
    }
    :host(.gs-etat-vide--filtre) .pictogramme {
      background: var(--gs-surface-2);
      color: var(--gs-encre-3);
    }
    mat-icon {
      width: 28px;
      height: 28px;
      font-size: 28px;
    }
    p {
      margin: 0;
      max-width: 46ch;
    }
    .titre {
      font-size: var(--gs-texte-lg);
      font-weight: 600;
      color: var(--gs-encre);
    }
    .texte {
      font-size: var(--gs-texte-md);
      color: var(--gs-encre-2);
    }
    .action {
      margin-top: var(--gs-esp-2);
    }
    .action:empty {
      display: none;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EtatVide {
  readonly icone = input('inbox');
  readonly titre = input.required<string>();
  readonly texte = input<string | null>(null);
  /** Vrai quand c'est un filtre qui a vide la liste, et non l'absence de donnees. */
  readonly filtre = input(false);
}
