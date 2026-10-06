import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';

/** Une barre : un libelle, une valeur, et ce qu'on dit a cote — la part, la marge. */
export interface Barre {
  libelle: string;
  valeur: number;
  detail?: string | null;
}

/**
 * Des barres horizontales, la plus grande en tete : ou se fait le chiffre.
 *
 * Horizontales parce que les libelles — un nom de categorie, de vendeur — se lisent couches, pas
 * debout. Chaque barre porte sa valeur ecrite : la longueur compare, le nombre renseigne. Au-dela
 * de `maximum` lignes, le reste se replie en une seule, « Autres » : une liste de quarante barres
 * ne se compare plus.
 */
@Component({
  selector: 'gs-barres',
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (lignes().length === 0) {
      <p class="vide">{{ vide() }}</p>
    } @else {
      <ul>
        @for (b of lignes(); track b.libelle) {
          <li>
            <div class="haut">
              <span class="libelle">{{ b.libelle }}</span>
              <span class="valeur nombre">{{ b.valeur | number: '1.0-0' }} {{ unite() }}</span>
            </div>
            <div class="piste" aria-hidden="true">
              <span class="barre" [style.width.%]="largeur(b.valeur)" [style.background]="couleur()"></span>
            </div>
            @if (b.detail) {
              <div class="detail">{{ b.detail }}</div>
            }
          </li>
        }
      </ul>
    }
  `,
  styles: `
    :host {
      display: block;
    }
    ul {
      display: grid;
      gap: 12px;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .haut {
      display: flex;
      align-items: baseline;
      gap: 8px;
      font-size: var(--gs-texte-sm);
    }
    .libelle {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      color: var(--gs-encre);
    }
    .valeur {
      font-weight: 600;
      color: var(--gs-encre);
    }
    .piste {
      height: 8px;
      margin-top: 4px;
      border-radius: 4px;
      background: var(--gs-surface-2);
    }
    .barre {
      display: block;
      height: 100%;
      min-width: 2px;
      border-radius: 4px;
    }
    .detail,
    .vide {
      margin: 2px 0 0;
      font-size: var(--gs-texte-xs);
      color: var(--gs-encre-3);
    }
  `,
})
export class Barres {
  readonly barres = input.required<Barre[]>();
  readonly couleur = input('var(--gs-serie-1)');
  readonly unite = input('F');
  readonly maximum = input(8);
  readonly vide = input('Aucune vente sur la période.');

  protected readonly lignes = computed(() => {
    const triees = [...this.barres()].filter((b) => b.valeur > 0).sort((a, b) => b.valeur - a.valeur);
    const max = this.maximum();
    if (triees.length <= max) return triees;
    const autres = triees.slice(max - 1);
    return [
      ...triees.slice(0, max - 1),
      {
        libelle: `Autres (${autres.length})`,
        valeur: autres.reduce((s, b) => s + b.valeur, 0),
        detail: null,
      },
    ];
  });

  private readonly plusGrande = computed(() => Math.max(1, ...this.lignes().map((b) => b.valeur)));

  protected largeur(valeur: number): number {
    return (valeur / this.plusGrande()) * 100;
  }
}

/** Une colonne : sa graduation (« 14 h », « lun. ») et sa valeur. */
export interface Colonne {
  libelle: string;
  valeur: number;
  detail?: string | null;
}

/**
 * Des colonnes sur un cycle — les heures de la journee, les jours de la semaine : quand vient le
 * client. Le survol ou le focus donne la valeur exacte ; la plus haute est nommee sous le titre.
 */
@Component({
  selector: 'gs-colonnes',
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="colonnes" [style.height.px]="hauteur()">
      @for (c of colonnes(); track c.libelle; let i = $index) {
        <button type="button" class="colonne" [attr.aria-label]="c.libelle + ' : ' + (c.valeur | number: '1.0-0') + ' ' + unite()"
                (pointerenter)="survol.set(i)" (pointerleave)="survol.set(null)" (focus)="survol.set(i)" (blur)="survol.set(null)">
          <span class="barre" [style.height.%]="hauteurDe(c.valeur)" [style.background]="couleur()"
                [class.estompee]="survol() !== null && survol() !== i"></span>
          @if (survol() === i) {
            <span class="bulle" role="status">
              <b>{{ c.libelle }}</b><br />
              <span class="nombre">{{ c.valeur | number: '1.0-0' }} {{ unite() }}</span>
              @if (c.detail) { <br /><span class="detail">{{ c.detail }}</span> }
            </span>
          }
        </button>
      }
    </div>
    <div class="graduations" aria-hidden="true">
      @for (c of colonnes(); track c.libelle; let i = $index) {
        <span>{{ i % pasDesGraduations() === 0 ? c.libelle : '' }}</span>
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
    .colonnes {
      display: flex;
      align-items: flex-end;
      gap: 2px;
      border-bottom: 1px solid var(--gs-grille);
    }
    .colonne {
      position: relative;
      display: flex;
      align-items: flex-end;
      flex: 1;
      height: 100%;
      padding: 0;
      border: 0;
      background: none;
      cursor: default;
    }
    .colonne:focus-visible {
      outline: 2px solid var(--gs-focus);
      outline-offset: 1px;
    }
    .barre {
      display: block;
      width: 100%;
      min-height: 1px;
      border-radius: 4px 4px 0 0;
      transition: opacity 120ms ease;
    }
    .barre.estompee {
      opacity: 0.45;
    }
    .bulle {
      position: absolute;
      bottom: calc(100% + 4px);
      left: 50%;
      z-index: 2;
      transform: translateX(-50%);
      padding: 6px 8px;
      border: 1px solid var(--gs-trait);
      border-radius: var(--gs-r-champ);
      background: var(--gs-surface);
      box-shadow: var(--gs-ombre-2);
      font-size: var(--gs-texte-xs);
      color: var(--gs-encre);
      white-space: nowrap;
      text-align: left;
      pointer-events: none;
    }
    .detail {
      color: var(--gs-encre-3);
    }
    .graduations {
      display: flex;
      gap: 2px;
      margin-top: 4px;
      font-size: 11px;
      color: var(--gs-encre-3);
    }
    .graduations span {
      flex: 1;
      overflow: visible;
      white-space: nowrap;
    }
    @media (prefers-reduced-motion: reduce) {
      .barre {
        transition: none;
      }
    }
  `,
})
export class Colonnes {
  readonly colonnes = input.required<Colonne[]>();
  readonly couleur = input('var(--gs-serie-1)');
  readonly unite = input('F');
  readonly hauteur = input(120);
  /** Une graduation sur combien : 24 heures n'en portent qu'une sur trois. */
  readonly pasDesGraduations = input(1);

  protected readonly survol = signal<number | null>(null);
  private readonly plusHaute = computed(() => Math.max(1, ...this.colonnes().map((c) => c.valeur)));

  protected hauteurDe(valeur: number): number {
    return (valeur / this.plusHaute()) * 100;
  }
}
