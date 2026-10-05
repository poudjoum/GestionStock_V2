import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { forkJoin } from 'rxjs';
import { Catalogue } from './catalogue.service';
import { messageDErreur } from '../noyau/erreurs';
import type { ArticleDto } from '../noyau/api';
import {
  CodeBarresDto,
  ConditionnementDto,
  libelleDuType,
  symbole,
  fractionnable,
  unites,
} from '../noyau/conditionnements';

/** Le formulaire d'un conditionnement, a plat. */
interface SaisieConditionnement {
  id: number | null;
  libelle: string;
  quantiteUnites: number | null;
  prixVenteHt: number | null;
  vendable: boolean;
  achetable: boolean;
}

function vide(): SaisieConditionnement {
  return { id: null, libelle: '', quantiteUnites: null, prixVenteHt: null, vendable: true, achetable: true };
}

/**
 * Les conditionnements d'un article et ses codes-barres, dans le volet de la fiche.
 *
 * Le gerant y passe une fois par article, la marchandise en main : il scanne le code de la
 * bouteille, puis celui du carton. Le champ du code est donc fait pour la douchette — elle tape et
 * envoie Entree —, et il rend le point apres chaque code pour enchainer.
 */
@Component({
  selector: 'app-conditionnements-article',
  imports: [
    DecimalPipe,
    FormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
  ],
  template: `
    @let a = article();
    @let s = saisie();
    @let unite = symbole(a);

    <!-- Les conditionnements -->
    <section class="mt-6">
      <h3 class="text-xs font-medium uppercase tracking-wide opacity-60">Vente en gros</h3>
      <p class="mt-1 text-xs opacity-70">
        Le stock se compte en <b>{{ unite }}</b>. Un conditionnement en regroupe plusieurs, à son propre prix.
      </p>

      @if (chargement()) {
        <div class="squelette mt-3 h-12"></div>
      } @else {
        <ul class="mt-3 flex list-none flex-col gap-2 p-0">
          @for (c of actifs(); track c.id) {
            <li class="carte flex items-center gap-2 px-3 py-2"
                [style.background]="s.id === c.id ? 'var(--mat-sys-secondary-container)' : ''">
              <span class="min-w-0 flex-1">
                <span class="block truncate text-sm font-medium">{{ c.libelle }}</span>
                <span class="block text-xs opacity-60">
                  {{ c.quantiteUnites | number: '1.0-3' }} {{ unites(a, c.quantiteUnites) }}
                  · {{ c.vendable ? 'vente' : '' }}{{ c.vendable && c.achetable ? ' et ' : '' }}{{ c.achetable ? 'achat' : '' }}
                  @if (codesDe(c.id!).length) {
                    · {{ codesDe(c.id!).length }} code{{ codesDe(c.id!).length > 1 ? 's' : '' }}
                  }
                </span>
              </span>
              @if (c.prixVenteHt != null) {
                <span class="nombre shrink-0 text-sm font-medium">{{ c.prixVenteHt | number: '1.0-0' }} F</span>
              }
              <button mat-icon-button (click)="modifier(c)" [attr.aria-label]="'Modifier ' + c.libelle">
                <mat-icon class="!h-5 !w-5 !text-xl">edit</mat-icon>
              </button>
              <button mat-icon-button (click)="retirer(c)" [attr.aria-label]="'Retirer ' + c.libelle">
                <mat-icon class="!h-5 !w-5 !text-xl">delete_outline</mat-icon>
              </button>
            </li>
          } @empty {
            <li class="text-xs opacity-60">Vendu seulement {{ fractionnable(a) ? 'au détail' : 'à l’unité' }}.</li>
          }
        </ul>

        <div class="carte mt-3 p-3">
          <p class="mb-2 text-sm font-medium">
            {{ s.id ? 'Modifier « ' + s.libelle + ' »' : 'Ajouter un conditionnement' }}
          </p>
          <mat-form-field appearance="outline" class="w-full" subscriptSizing="dynamic">
            <mat-label>Nom</mat-label>
            <input matInput [ngModel]="s.libelle" (ngModelChange)="champ('libelle', $event)"
                   placeholder="Carton de 24" maxlength="60" />
          </mat-form-field>
          <div class="mt-3 flex gap-2">
            <mat-form-field appearance="outline" class="min-w-0 flex-1" subscriptSizing="dynamic">
              <mat-label>Contient</mat-label>
              <input matInput type="number" inputmode="decimal" min="0"
                     [ngModel]="s.quantiteUnites" (ngModelChange)="champ('quantiteUnites', $event)" />
              <span matTextSuffix>{{ unite }}</span>
            </mat-form-field>
            <mat-form-field appearance="outline" class="min-w-0 flex-1" subscriptSizing="dynamic">
              <mat-label>Prix HT</mat-label>
              <input matInput type="number" inputmode="decimal" min="0" [disabled]="!s.vendable"
                     [ngModel]="s.prixVenteHt" (ngModelChange)="champ('prixVenteHt', $event)" />
              <span matTextSuffix>F</span>
            </mat-form-field>
          </div>
          <!-- Ce que le carton fait gagner ou perdre au client, par rapport a l'unite. -->
          @if (comparaison(); as c) {
            <p class="mt-1 text-xs opacity-70">
              Soit <span class="nombre">{{ c.parUnite | number: '1.0-0' }} F</span> le {{ unite }}
              @if (c.remise > 0) {
                — <b>{{ c.remise | number: '1.0-1' }} %</b> de moins qu’à l’unité
              } @else if (c.remise < 0) {
                — plus cher qu’à l’unité
              }
            </p>
          }
          <div class="mt-2 flex flex-wrap gap-x-4">
            <mat-checkbox [checked]="s.vendable" (change)="champ('vendable', $event.checked)">Se vend</mat-checkbox>
            <mat-checkbox [checked]="s.achetable" (change)="champ('achetable', $event.checked)">S’achète</mat-checkbox>
          </div>
          @if (erreurConditionnement(); as m) {
            <p class="mt-2 rounded px-3 py-2 text-sm" role="alert"
               style="background: var(--mat-sys-error-container); color: var(--mat-sys-on-error-container)">{{ m }}</p>
          }
          <div class="mt-3 flex gap-2">
            <button mat-flat-button class="flex-1" [disabled]="!conditionnementComplet() || envoi()"
                    (click)="enregistrerConditionnement()">
              {{ s.id ? 'Enregistrer' : 'Ajouter' }}
            </button>
            @if (s.id) {
              <button mat-stroked-button (click)="saisie.set(vide())">Annuler</button>
            }
          </div>
        </div>
      }
    </section>

    <!-- Les codes-barres -->
    <section class="mt-6">
      <h3 class="text-xs font-medium uppercase tracking-wide opacity-60">Codes-barres</h3>
      <p class="mt-1 text-xs opacity-70">
        Scannez l’étiquette avec la douchette : chaque code dit ce qu’il désigne, l’unité ou un conditionnement.
      </p>

      <ul class="mt-3 flex list-none flex-col gap-1 p-0">
        @for (code of codes(); track code.id) {
          <li class="flex items-center gap-2 text-sm">
            <mat-icon class="!h-5 !w-5 !text-xl opacity-60" aria-hidden="true">
              {{ code.type === 'QR' ? 'qr_code_2' : 'view_week' }}
            </mat-icon>
            <span class="min-w-0 flex-1">
              <span class="code-barres block truncate">{{ code.code }}</span>
              <span class="block truncate text-xs opacity-60">{{ designe(code) }} · {{ libelleDuType(code.type) }}</span>
            </span>
            <button mat-icon-button (click)="retirerCode(code)" [attr.aria-label]="'Retirer le code ' + code.code">
              <mat-icon class="!h-5 !w-5 !text-xl">close</mat-icon>
            </button>
          </li>
        } @empty {
          <li class="text-xs opacity-60">
            Aucun code : le comptoir reconnaît l’article à son code {{ a.codeArticle }}.
          </li>
        }
      </ul>

      <div class="mt-3 flex gap-2">
        <mat-form-field appearance="outline" class="min-w-0 flex-1" subscriptSizing="dynamic">
          <mat-label>Scanner ou saisir</mat-label>
          <mat-icon matPrefix>barcode_reader</mat-icon>
          <input #champCode matInput autocomplete="off" autocapitalize="none" inputmode="numeric"
                 [ngModel]="nouveauCode()" (ngModelChange)="nouveauCode.set($event)"
                 (keydown.enter)="ajouterCode(champCode)" />
        </mat-form-field>
        <mat-form-field appearance="outline" class="w-36 shrink-0" subscriptSizing="dynamic">
          <mat-label>Désigne</mat-label>
          <mat-select [value]="cible()" (valueChange)="cible.set($event)">
            <mat-option [value]="0">{{ unite === 'pièce' ? 'L’unité' : 'Le ' + unite }}</mat-option>
            @for (c of actifs(); track c.id) {
              <mat-option [value]="c.id">{{ c.libelle }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </div>
      @if (erreurCode(); as m) {
        <p class="mt-2 rounded px-3 py-2 text-sm" role="alert"
           style="background: var(--mat-sys-error-container); color: var(--mat-sys-on-error-container)">{{ m }}</p>
      }
      <div class="mt-2 flex flex-wrap gap-2">
        <button mat-stroked-button [disabled]="!nouveauCode().trim() || envoi()" (click)="ajouterCode(champCode)">
          <mat-icon>add</mat-icon> Ajouter le code
        </button>
        <!-- Pour le vrac, le fait-maison, ce que le fournisseur livre sans etiquette. -->
        <button mat-button [disabled]="envoi()" (click)="genererCode()">
          <mat-icon>auto_awesome</mat-icon> Produit sans étiquette : créer un code
        </button>
      </div>
    </section>
  `,
  styles: `
    .code-barres {
      font-family: 'JetBrains Mono', ui-monospace, monospace;
      font-variant-numeric: tabular-nums;
      letter-spacing: 0.02em;
    }
  `,
})
export class ConditionnementsArticle {
  private readonly service = inject(Catalogue);

  /** L'article enregistre : il a un identifiant, et son unite fait foi. */
  readonly article = input.required<ArticleDto>();
  /** Quelque chose a change : la liste du catalogue se recharge pour l'afficher. */
  readonly change = output<void>();

  protected readonly symbole = symbole;
  protected readonly unites = unites;
  protected readonly fractionnable = fractionnable;
  protected readonly libelleDuType = libelleDuType;
  protected readonly vide = vide;

  protected readonly conditionnements = signal<ConditionnementDto[]>([]);
  protected readonly codes = signal<CodeBarresDto[]>([]);
  protected readonly chargement = signal(true);
  protected readonly envoi = signal(false);

  protected readonly saisie = signal<SaisieConditionnement>(vide());
  protected readonly erreurConditionnement = signal<string | null>(null);

  protected readonly nouveauCode = signal('');
  /** 0 : le code designe l'unite de base. Pas `null`, que le selecteur affiche comme vide. */
  protected readonly cible = signal<number>(0);
  protected readonly erreurCode = signal<string | null>(null);

  /** Les retires ne se montrent plus : ils ne vivent que dans l'historique des ventes. */
  protected readonly actifs = computed(() => this.conditionnements().filter((c) => c.actif !== false));

  protected readonly conditionnementComplet = computed(() => {
    const s = this.saisie();
    return !!s.libelle.trim() && (s.quantiteUnites ?? 0) > 0 && (!s.vendable || s.prixVenteHt != null)
      && (s.vendable || s.achetable);
  });

  /** Le prix du carton ramene a l'unite, et l'ecart avec le prix a l'unite. */
  protected readonly comparaison = computed(() => {
    const s = this.saisie();
    const prixUnite = this.article().prixUnitaireHt;
    if (!s.vendable || s.prixVenteHt == null || !s.quantiteUnites || !prixUnite) {
      return null;
    }
    const parUnite = s.prixVenteHt / s.quantiteUnites;
    return { parUnite, remise: (1 - parUnite / prixUnite) * 100 };
  });

  constructor() {
    // Un autre article ouvert dans le volet : on recharge, et l'on oublie la saisie en cours.
    effect(() => {
      const id = this.article().id;
      untracked(() => {
        this.conditionnements.set([]);
        this.codes.set([]);
        this.saisie.set(vide());
        this.nouveauCode.set('');
        this.cible.set(0);
        this.erreurCode.set(null);
        this.erreurConditionnement.set(null);
        if (id != null) {
          this.charger(id);
        }
      });
    });
  }

  protected champ<K extends keyof SaisieConditionnement>(cle: K, valeur: SaisieConditionnement[K]): void {
    this.saisie.update((s) => ({ ...s, [cle]: valeur }));
  }

  protected codesDe(idConditionnement: number): CodeBarresDto[] {
    return this.codes().filter((c) => c.idConditionnement === idConditionnement);
  }

  protected designe(code: CodeBarresDto): string {
    if (code.idConditionnement == null) {
      return 'unité';
    }
    return this.conditionnements().find((c) => c.id === code.idConditionnement)?.libelle ?? '—';
  }

  protected modifier(c: ConditionnementDto): void {
    this.erreurConditionnement.set(null);
    this.saisie.set({
      id: c.id ?? null,
      libelle: c.libelle ?? '',
      quantiteUnites: c.quantiteUnites ?? null,
      prixVenteHt: c.prixVenteHt ?? null,
      vendable: c.vendable !== false,
      achetable: c.achetable !== false,
    });
  }

  protected enregistrerConditionnement(): void {
    const s = this.saisie();
    const id = this.article().id!;
    const corps: ConditionnementDto = {
      id: s.id ?? undefined,
      libelle: s.libelle.trim(),
      quantiteUnites: s.quantiteUnites!,
      prixVenteHt: s.vendable ? s.prixVenteHt! : undefined,
      vendable: s.vendable,
      achetable: s.achetable,
    };
    this.envoi.set(true);
    this.erreurConditionnement.set(null);
    const appel = s.id
      ? this.service.modifierConditionnement(id, corps)
      : this.service.ajouterConditionnement(id, corps);
    appel.subscribe({
      next: () => {
        this.envoi.set(false);
        this.saisie.set(vide());
        this.charger(id);
        this.change.emit();
      },
      error: (echec: unknown) => {
        this.envoi.set(false);
        this.erreurConditionnement.set(messageDErreur(echec, 'Le conditionnement n’a pas pu être enregistré.'));
      },
    });
  }

  protected retirer(c: ConditionnementDto): void {
    const id = this.article().id!;
    this.service.retirerConditionnement(id, c.id!).subscribe({
      next: () => {
        if (this.saisie().id === c.id) {
          this.saisie.set(vide());
        }
        this.charger(id);
        this.change.emit();
      },
      error: (echec: unknown) =>
        this.erreurConditionnement.set(messageDErreur(echec, 'Le conditionnement n’a pas pu être retiré.')),
    });
  }

  protected ajouterCode(champ: HTMLInputElement): void {
    const code = this.nouveauCode().trim();
    if (!code || this.envoi()) {
      return;
    }
    const id = this.article().id!;
    this.envoi.set(true);
    this.erreurCode.set(null);
    this.service.ajouterCode(id, { code, idConditionnement: this.cible() || undefined }).subscribe({
      next: () => {
        this.envoi.set(false);
        this.nouveauCode.set('');
        this.charger(id);
        this.change.emit();
        // On enchaine : le code du carton suit celui de la bouteille.
        champ.focus();
      },
      error: (echec: unknown) => {
        this.envoi.set(false);
        // « Le code … est déjà attribué », « n'est pas un EAN13 valide » : le serveur dit quoi faire.
        this.erreurCode.set(messageDErreur(echec, 'Le code n’a pas pu être ajouté.'));
        champ.select();
      },
    });
  }

  protected genererCode(): void {
    const id = this.article().id!;
    this.envoi.set(true);
    this.erreurCode.set(null);
    this.service.genererCodeInterne(id, this.cible() || null).subscribe({
      next: () => {
        this.envoi.set(false);
        this.charger(id);
        this.change.emit();
      },
      error: (echec: unknown) => {
        this.envoi.set(false);
        this.erreurCode.set(messageDErreur(echec, 'Le code n’a pas pu être créé.'));
      },
    });
  }

  protected retirerCode(code: CodeBarresDto): void {
    const id = this.article().id!;
    this.service.retirerCode(id, code.id!).subscribe({
      next: () => {
        this.charger(id);
        this.change.emit();
      },
      error: (echec: unknown) => this.erreurCode.set(messageDErreur(echec, 'Le code n’a pas pu être retiré.')),
    });
  }

  private charger(id: number): void {
    this.chargement.set(this.conditionnements().length === 0 && this.codes().length === 0);
    forkJoin([this.service.conditionnements(id), this.service.codes(id)]).subscribe({
      next: ([conditionnements, codes]) => {
        this.conditionnements.set(conditionnements);
        this.codes.set(codes);
        this.chargement.set(false);
      },
      error: (echec: unknown) => {
        this.chargement.set(false);
        this.erreurConditionnement.set(messageDErreur(echec, 'Les conditionnements n’ont pas pu être chargés.'));
      },
    });
  }
}
