import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';

/** Une serie de la courbe. `couleur` est une variable CSS : `var(--gs-serie-1)`. */
export interface SerieCourbe {
  nom: string;
  valeurs: number[];
  couleur: string;
  /** Une serie de reference — la periode precedente — se trace en pointilles. */
  pointilles?: boolean;
}

const HAUTEUR = 240;
const MARGE = { haut: 12, droite: 12, bas: 28, gauche: 56 };

/**
 * La courbe : une ou plusieurs series de montants sur le temps, un seul axe.
 *
 * Un seul axe, toujours : deux montants en francs se lisent sur la meme echelle, et deux echelles
 * feraient dire au dessin ce que les chiffres ne disent pas. L'axe part de zero — une courbe qui
 * commencerait a 80 000 ferait d'une baisse de 3 % un effondrement.
 *
 * Le survol donne les valeurs exactes du point le plus proche, au clavier aussi (fleches). Le
 * tableau des valeurs remplace le dessin pour qui ne le voit pas.
 */
@Component({
  selector: 'gs-courbe',
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="legende" aria-hidden="true">
      @for (s of series(); track s.nom) {
        <span class="legende-item">
          <svg width="18" height="8"><line x1="1" y1="4" x2="17" y2="4" [attr.stroke]="s.couleur" stroke-width="2"
               stroke-linecap="round" [attr.stroke-dasharray]="s.pointilles ? '3 3' : null" /></svg>
          {{ s.nom }}
        </span>
      }
    </div>

    <div class="zone" [style.height.px]="hauteur">
      <svg [attr.width]="largeur()" [attr.height]="hauteur" role="img" [attr.aria-label]="description()"
           tabindex="0" (keydown)="clavier($event)" (focus)="survol.set(survol() ?? dernier())" (blur)="survol.set(null)"
           (pointermove)="pointer($event)" (pointerleave)="survol.set(null)">
        <!-- La grille : recessive, elle aide a lire sans se faire voir. -->
        @for (g of graduations(); track g.valeur) {
          <line class="grille" [attr.x1]="marge.gauche" [attr.x2]="largeur() - marge.droite" [attr.y1]="g.y" [attr.y2]="g.y" />
          <text class="axe" [attr.x]="marge.gauche - 8" [attr.y]="g.y" text-anchor="end" dominant-baseline="middle">{{ g.texte }}</text>
        }
        @for (e of etiquettes(); track e.i) {
          <text class="axe" [attr.x]="x(e.i)" [attr.y]="hauteur - 8" [attr.text-anchor]="e.ancre">{{ e.texte }}</text>
        }

        <!-- La reference d'abord, dessous ; la serie courante par-dessus. -->
        @for (s of ordreDeTrace(); track s.nom) {
          <path [attr.d]="chemin(s.valeurs)" fill="none" [attr.stroke]="s.couleur" stroke-width="2"
                stroke-linejoin="round" stroke-linecap="round" [attr.stroke-dasharray]="s.pointilles ? '4 4' : null" />
        }

        @if (survol(); as i) {
          <line class="repere" [attr.x1]="x(i - 1)" [attr.x2]="x(i - 1)" [attr.y1]="marge.haut" [attr.y2]="hauteur - marge.bas" />
          @for (s of series(); track s.nom) {
            @if (s.valeurs[i - 1] != null) {
              <circle [attr.cx]="x(i - 1)" [attr.cy]="y(s.valeurs[i - 1])" r="4.5" [attr.fill]="s.couleur" class="point" />
            }
          }
        }
      </svg>

      @if (survol(); as i) {
        <div class="bulle" [style.left.px]="bulleX(i - 1)" role="status">
          <div class="bulle-titre">{{ libelles()[i - 1] }}</div>
          @for (s of series(); track s.nom) {
            <div class="bulle-ligne">
              <span class="pastille" [style.background]="s.couleur"></span>
              <span class="bulle-nom">{{ s.nom }}</span>
              <span class="nombre">{{ s.valeurs[i - 1] | number: '1.0-0' }} {{ unite() }}</span>
            </div>
          }
        </div>
      }
    </div>

    <details class="tableau">
      <summary>Voir les valeurs</summary>
      <table class="gs-tableau">
        <thead>
          <tr>
            <th>{{ titreAbscisse() }}</th>
            @for (s of series(); track s.nom) {
              <th class="gs-num">{{ s.nom }}</th>
            }
          </tr>
        </thead>
        <tbody>
          @for (l of libelles(); track $index; let i = $index) {
            <tr>
              <td>{{ l }}</td>
              @for (s of series(); track s.nom) {
                <td class="gs-num nombre">{{ s.valeurs[i] | number: '1.0-0' }}</td>
              }
            </tr>
          }
        </tbody>
      </table>
    </details>
  `,
  styles: `
    :host {
      display: block;
    }
    .legende {
      display: flex;
      flex-wrap: wrap;
      gap: 4px 16px;
      margin-bottom: 8px;
      font-size: var(--gs-texte-sm);
      color: var(--gs-encre-2);
    }
    .legende-item {
      display: inline-flex;
      align-items: center;
      gap: 6px;
    }
    .zone {
      position: relative;
    }
    svg {
      display: block;
      overflow: visible;
      outline: none;
      touch-action: pan-y;
    }
    svg:focus-visible {
      outline: 3px solid var(--gs-focus);
      outline-offset: 2px;
      border-radius: 4px;
    }
    .grille {
      stroke: var(--gs-grille);
      stroke-width: 1;
    }
    .axe {
      font-size: 11px;
      fill: var(--gs-encre-3);
      font-variant-numeric: tabular-nums;
    }
    .repere {
      stroke: var(--gs-encre-3);
      stroke-width: 1;
      stroke-dasharray: 2 3;
    }
    .point {
      stroke: var(--gs-surface);
      stroke-width: 2;
    }
    .bulle {
      position: absolute;
      top: 0;
      z-index: 2;
      min-width: 160px;
      padding: 8px 10px;
      border: 1px solid var(--gs-trait);
      border-radius: var(--gs-r-champ);
      background: var(--gs-surface);
      box-shadow: var(--gs-ombre-2);
      font-size: var(--gs-texte-sm);
      color: var(--gs-encre);
      pointer-events: none;
    }
    .bulle-titre {
      margin-bottom: 4px;
      font-weight: 600;
    }
    .bulle-ligne {
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .bulle-nom {
      flex: 1;
      color: var(--gs-encre-2);
    }
    .pastille {
      width: 8px;
      height: 8px;
      border-radius: 2px;
    }
    .tableau {
      margin-top: 8px;
      font-size: var(--gs-texte-sm);
    }
    .tableau summary {
      cursor: pointer;
      color: var(--gs-encre-2);
    }
    .tableau table {
      margin-top: 8px;
    }
  `,
})
export class Courbe implements OnInit, OnDestroy {
  private readonly hote = inject(ElementRef<HTMLElement>);
  private observateur?: ResizeObserver;

  readonly series = input.required<SerieCourbe[]>();
  /** Le libelle de chaque point, dans la bulle et le tableau : « lun. 6 oct. ». */
  readonly libelles = input.required<string[]>();
  /** Les libelles courts sous l'axe : « 6 oct. ». A defaut, les libelles. */
  readonly libellesCourts = input<string[] | null>(null);
  readonly unite = input('F');
  readonly titreAbscisse = input('Période');
  readonly description = input('Courbe');

  protected readonly hauteur = HAUTEUR;
  protected readonly marge = MARGE;
  protected readonly largeur = signal(600);
  /** Le point survole, compte a partir de 1 (0 serait faux dans un @if). */
  protected readonly survol = signal<number | null>(null);

  private readonly nombre = computed(() => Math.max(0, ...this.series().map((s) => s.valeurs.length)));
  protected readonly dernier = computed(() => (this.nombre() > 0 ? this.nombre() : null));
  protected readonly ordreDeTrace = computed(() =>
    [...this.series()].sort((a, b) => Number(!!b.pointilles) - Number(!!a.pointilles)),
  );

  /** Le haut de l'axe, arrondi a une valeur ronde : 0, 25 000, 50 000… */
  private readonly maximum = computed(() => {
    const max = Math.max(0, ...this.series().flatMap((s) => s.valeurs.filter((v) => v != null)));
    return arrondiHaut(max || 1);
  });

  protected readonly graduations = computed(() => {
    const max = this.maximum();
    return [0, 0.25, 0.5, 0.75, 1].map((f) => ({ valeur: max * f, y: this.y(max * f), texte: compact(max * f) }));
  });

  /** Quelques dates sous l'axe, pas une par point : elles se chevaucheraient. */
  protected readonly etiquettes = computed(() => {
    const n = this.nombre();
    const textes = this.libellesCourts() ?? this.libelles();
    if (n === 0) return [];
    const place = Math.max(1, Math.floor((this.largeur() - MARGE.gauche - MARGE.droite) / 70));
    const pas = Math.max(1, Math.ceil(n / place));
    const rangs: number[] = [];
    for (let i = 0; i < n; i += pas) rangs.push(i);
    if (rangs[rangs.length - 1] !== n - 1 && n - 1 - rangs[rangs.length - 1] >= pas / 2) rangs.push(n - 1);
    return rangs.map((i) => ({
      i,
      texte: textes[i] ?? '',
      ancre: n === 1 ? 'middle' : i === 0 ? 'start' : i === n - 1 ? 'end' : 'middle',
    }));
  });

  ngOnInit(): void {
    this.observateur = new ResizeObserver((entrees) => {
      const largeur = Math.floor(entrees[0]?.contentRect.width ?? 600);
      if (largeur > 0) this.largeur.set(largeur);
    });
    this.observateur.observe(this.hote.nativeElement);
  }

  ngOnDestroy(): void {
    this.observateur?.disconnect();
  }

  protected x(i: number): number {
    const n = this.nombre();
    const utile = this.largeur() - MARGE.gauche - MARGE.droite;
    return n <= 1 ? MARGE.gauche + utile / 2 : MARGE.gauche + (utile * i) / (n - 1);
  }

  protected y(valeur: number): number {
    const utile = HAUTEUR - MARGE.haut - MARGE.bas;
    return MARGE.haut + utile * (1 - Math.max(0, valeur) / this.maximum());
  }

  protected chemin(valeurs: number[]): string {
    return valeurs.map((v, i) => `${i === 0 ? 'M' : 'L'}${this.x(i).toFixed(1)},${this.y(v ?? 0).toFixed(1)}`).join(' ');
  }

  /** La bulle a cote du point, du cote ou elle tient. */
  protected bulleX(i: number): number {
    const x = this.x(i);
    return x > this.largeur() / 2 ? x - 176 : x + 12;
  }

  protected pointer(evenement: PointerEvent): void {
    const n = this.nombre();
    if (n === 0) return;
    const rect = (evenement.currentTarget as SVGElement).getBoundingClientRect();
    const utile = this.largeur() - MARGE.gauche - MARGE.droite;
    const rang = n <= 1 ? 0 : Math.round(((evenement.clientX - rect.left - MARGE.gauche) / utile) * (n - 1));
    this.survol.set(Math.min(n - 1, Math.max(0, rang)) + 1);
  }

  protected clavier(evenement: KeyboardEvent): void {
    const n = this.nombre();
    const courant = this.survol() ?? n;
    if (evenement.key === 'ArrowLeft') {
      this.survol.set(Math.max(1, courant - 1));
      evenement.preventDefault();
    } else if (evenement.key === 'ArrowRight') {
      this.survol.set(Math.min(n, courant + 1));
      evenement.preventDefault();
    }
  }
}

/** 87 300 donne 100 000 ; 1 240 donne 1 500 : un haut d'axe rond, juste au-dessus du maximum. */
export function arrondiHaut(valeur: number): number {
  const puissance = Math.pow(10, Math.floor(Math.log10(valeur)));
  for (const pas of [1, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10]) {
    if (pas * puissance >= valeur) return pas * puissance;
  }
  return 10 * puissance;
}

/** 1 250 000 en « 1,25 M », 45 000 en « 45 k » : l'axe ne porte pas de longs nombres. */
export function compact(valeur: number): string {
  const format = (v: number) => v.toLocaleString('fr-FR', { maximumFractionDigits: 2 });
  if (valeur >= 1_000_000) return `${format(valeur / 1_000_000)} M`;
  if (valeur >= 1_000) return `${format(valeur / 1_000)} k`;
  return format(valeur);
}
