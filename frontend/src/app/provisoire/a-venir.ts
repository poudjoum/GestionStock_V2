import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';

/**
 * L'ecran des routes encore a ecrire.
 *
 * Il existe pour que le menu mene quelque part des maintenant : une entree qui tombe sur une
 * route inconnue donne l'impression d'une application cassee, la ou celle-ci dit simplement ce
 * qui vient.
 */
@Component({
  selector: 'app-a-venir',
  imports: [MatIconModule],
  template: `
    <div class="py-20 text-center opacity-60">
      <mat-icon class="!h-12 !w-12 !text-5xl">construction</mat-icon>
      <h1 class="mt-2 text-lg font-medium">{{ titre }}</h1>
      <p class="mt-1 text-sm">Cet écran arrive dans un prochain lot.</p>
    </div>
  `,
})
export class AVenir {
  protected readonly titre =
    (inject(ActivatedRoute).snapshot.data['titre'] as string) ?? 'À venir';
}
