import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Une petite courbe dans une ligne de tableau : la forme d'une serie, sans axe ni graduation.
 *
 * Elle repond a une seule question — ca monte, ca stagne, ca s'eteint ? — et laisse les chiffres a
 * la colonne voisine. Le dernier point est marque : c'est la semaine qu'on regarde. Le libelle
 * accessible dit les valeurs, que le dessin ne montre pas.
 */
@Component({
  selector: 'gs-tendance',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg [attr.width]="largeur()" [attr.height]="hauteur()" role="img" [attr.aria-label]="description()">
      <line [attr.x1]="1" [attr.x2]="largeur() - 1" [attr.y1]="hauteur() - 1" [attr.y2]="hauteur() - 1" class="base" />
      @if (points(); as p) {
        <polyline [attr.points]="p.trace" fill="none" [attr.stroke]="couleur()" stroke-width="2"
                  stroke-linejoin="round" stroke-linecap="round" />
        <circle [attr.cx]="p.dernierX" [attr.cy]="p.dernierY" r="3" [attr.fill]="couleur()" />
      }
    </svg>
  `,
  styles: `
    :host {
      display: inline-block;
      line-height: 0;
    }
    .base {
      stroke: var(--gs-grille);
      stroke-width: 1;
    }
  `,
})
export class Tendance {
  readonly valeurs = input.required<number[]>();
  readonly largeur = input(88);
  readonly hauteur = input(24);
  readonly couleur = input('var(--gs-serie-1)');
  readonly unite = input('ventes');

  protected readonly points = computed(() => {
    const v = this.valeurs();
    if (v.length === 0) return null;
    const max = Math.max(1, ...v);
    const l = this.largeur() - 6;
    const h = this.hauteur() - 6;
    const coord = v.map((val, i) => [3 + (v.length === 1 ? l / 2 : (l * i) / (v.length - 1)), 3 + h * (1 - val / max)]);
    const [dx, dy] = coord[coord.length - 1];
    return { trace: coord.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' '), dernierX: dx, dernierY: dy };
  });

  protected readonly description = computed(
    () => `${this.unite()} par semaine, de la plus ancienne à la plus récente : ${this.valeurs().join(', ')}`,
  );
}
