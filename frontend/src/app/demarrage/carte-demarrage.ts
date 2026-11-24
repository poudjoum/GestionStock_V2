import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Demarrage, DemarrageDto, ETAPES, prochaineEtape } from './demarrage.service';

/**
 * Le rappel de l'assistant, en tete de l'accueil du gerant.
 *
 * Il ne montre que l'etape a faire maintenant et l'avancement : la liste complete est sur
 * `/demarrage`. Il disparait de lui-meme quand les etapes indispensables sont faites, ou quand le
 * gerant le ferme — choix garde sur le serveur, pour ne pas le revoir sur un autre appareil.
 */
@Component({
  selector: 'app-carte-demarrage',
  imports: [RouterLink, MatButtonModule, MatIconModule],
  template: `
    @let e = etat();
    @if (e && !e.masque && !e.termine) {
      @let p = prochaine();
      <section class="carte" aria-labelledby="titre-demarrage">
        <div class="entete">
          <mat-icon class="fusee" aria-hidden="true">rocket_launch</mat-icon>
          <div class="textes">
            <h2 id="titre-demarrage">Mettre le magasin en route</h2>
            <p>{{ faites() }} sur {{ total() }} étapes faites</p>
          </div>
          <button
            mat-icon-button
            type="button"
            class="fermer"
            aria-label="Masquer l’assistant de l’accueil"
            title="Masquer de l’accueil"
            (click)="masquer()"
          >
            <mat-icon>close</mat-icon>
          </button>
        </div>
        <div class="barre" aria-hidden="true">
          <i [style.width.%]="(faites() / (total() || 1)) * 100"></i>
        </div>
        @if (p) {
          @let d = etapes[p.code];
          <div class="suite">
            <mat-icon class="icone" aria-hidden="true">{{ d.icone }}</mat-icon>
            <div class="suite-textes">
              <strong>{{ d.titre }}</strong>
              <span>{{ d.texte }}</span>
            </div>
            <div class="actions">
              <a mat-flat-button [routerLink]="d.chemin">{{ d.action }}</a>
              <a mat-button routerLink="/demarrage">Toutes les étapes</a>
            </div>
          </div>
        }
      </section>
    }
  `,
  styles: `
    :host {
      display: block;
    }
    .carte {
      display: grid;
      gap: var(--gs-esp-3);
      margin-bottom: var(--gs-esp-4);
      padding: var(--gs-esp-4) var(--gs-esp-5);
      border: 1px solid color-mix(in srgb, var(--gs-accent) 40%, var(--gs-trait));
      border-radius: var(--gs-r-carte);
      background: var(--gs-accent-doux);
      color: var(--gs-sur-accent-doux);
    }
    .entete {
      display: flex;
      align-items: center;
      gap: var(--gs-esp-3);
    }
    .fusee {
      color: var(--gs-accent);
    }
    .textes {
      flex: 1;
      min-width: 0;
    }
    h2 {
      margin: 0;
      font: 600 var(--gs-texte-lg) / 1.3 var(--gs-police-texte);
    }
    .textes p {
      margin: 0;
      font-size: var(--gs-texte-sm);
      color: var(--gs-encre-2);
    }
    .fermer {
      margin: -8px -12px -8px 0;
    }
    .barre {
      height: 6px;
      border-radius: 3px;
      background: var(--gs-surface);
      overflow: hidden;
    }
    .barre i {
      display: block;
      height: 100%;
      border-radius: 3px;
      background: var(--gs-vert-600);
    }
    .suite {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--gs-esp-3);
    }
    .icone {
      color: var(--gs-encre-2);
    }
    .suite-textes {
      display: grid;
      flex: 1 1 260px;
      min-width: 0;
      font-size: var(--gs-texte-md);
      color: var(--gs-encre);
    }
    .suite-textes span {
      color: var(--gs-encre-2);
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--gs-esp-2);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CarteDemarrage implements OnInit {
  private readonly service = inject(Demarrage);

  protected readonly etat = signal<DemarrageDto | null>(null);
  protected readonly etapes = ETAPES;

  private readonly obligatoires = computed(
    () => this.etat()?.etapes.filter((e) => !e.facultative) ?? [],
  );
  protected readonly total = computed(() => this.obligatoires().length);
  protected readonly faites = computed(() => this.obligatoires().filter((e) => e.faite).length);
  protected readonly prochaine = computed(() => {
    const etat = this.etat();
    return etat ? prochaineEtape(etat) : null;
  });

  ngOnInit(): void {
    // Un echec ne se dit pas : l'accueil se passe tres bien de ce rappel.
    this.service.etat().subscribe({ next: (e) => this.etat.set(e), error: () => undefined });
  }

  protected masquer(): void {
    const avant = this.etat();
    // La carte part tout de suite ; elle revient si le serveur n'a pas retenu le choix.
    if (avant) this.etat.set({ ...avant, masque: true });
    this.service.masquer(true).subscribe({
      next: (e) => this.etat.set(e),
      error: () => this.etat.set(avant),
    });
  }
}
