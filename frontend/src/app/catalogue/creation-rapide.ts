import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Observable, catchError, map, of, switchMap, tap } from 'rxjs';
import { messageDErreur } from '../noyau/erreurs';
import { UNITES, symbole, type UniteMesure } from '../noyau/conditionnements';
import { ArticleDto, Catalogue, CategoryDto } from './catalogue.service';

/** Ce qu'on donne a la fenetre : le code qu'on vient de lire, et qu'aucun article ne porte. */
export interface DonneesCreationRapide {
  code: string;
}

/** Ce qu'elle rend : l'article cree, et si son code-barres a pu lui etre rattache. */
export interface ArticleCreeRapidement {
  article: ArticleDto;
  codeRattache: boolean;
}

/** La valeur du choix « nouvelle catégorie » dans la liste. */
const NOUVELLE = -1;

/**
 * Creer un article depuis un code-barres que le catalogue ne connait pas.
 *
 * On scanne dans les rayons, le code est inconnu : plutot que de quitter l'inventaire pour le
 * catalogue, on remplit l'essentiel ici — ce qu'il faut pour que l'article existe et se compte.
 * Le code lu lui est rattache a l'enregistrement : le scan suivant le reconnaitra. Le reste de la
 * fiche (TVA propre, seuil, lots, cartons) se complete ensuite dans le catalogue.
 *
 * La categorie peut se creer au passage : un commerce qui demarre n'en a souvent aucune.
 */
@Component({
  selector: 'app-creation-rapide-article',
  imports: [
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
  ],
  template: `
    <h2 mat-dialog-title>Nouvel article</h2>
    <mat-dialog-content>
      <p class="lu">
        <mat-icon aria-hidden="true">qr_code_scanner</mat-icon>
        <span
          >Code lu : <span class="gs-code">{{ donnees.code }}</span></span
        >
      </p>

      <mat-form-field appearance="outline" class="w-full" subscriptSizing="dynamic">
        <mat-label>Désignation</mat-label>
        <input
          matInput
          cdkFocusInitial
          [ngModel]="designation()"
          (ngModelChange)="designation.set($event)"
          placeholder="Sac de ciment 50 kg"
        />
      </mat-form-field>

      <mat-form-field appearance="outline" class="mt-4 w-full" subscriptSizing="dynamic">
        <mat-label>Code de l’article</mat-label>
        <input
          matInput
          autocapitalize="characters"
          autocorrect="off"
          [ngModel]="codeArticle()"
          (ngModelChange)="codeArticle.set($event)"
        />
        <mat-hint>Unique dans le catalogue. Le code lu suffit, ou un code plus court.</mat-hint>
      </mat-form-field>

      <mat-form-field appearance="outline" class="mt-4 w-full" subscriptSizing="dynamic">
        <mat-label>Catégorie</mat-label>
        <mat-select [value]="idCategorie()" (valueChange)="idCategorie.set($event)">
          @for (c of categories(); track c.id) {
            <mat-option [value]="c.id">{{ c.designation || c.codeCategorie }}</mat-option>
          }
          <mat-option [value]="NOUVELLE">
            <mat-icon>add</mat-icon>
            Nouvelle catégorie…
          </mat-option>
        </mat-select>
      </mat-form-field>

      @if (idCategorie() === NOUVELLE) {
        <mat-form-field appearance="outline" class="mt-4 w-full" subscriptSizing="dynamic">
          <mat-label>Nom de la nouvelle catégorie</mat-label>
          <input
            matInput
            [ngModel]="nouvelleCategorie()"
            (ngModelChange)="nouvelleCategorie.set($event)"
            placeholder="Matériaux"
          />
        </mat-form-field>
      }

      <div class="mt-4 flex gap-2">
        <mat-form-field appearance="outline" class="flex-1" subscriptSizing="dynamic">
          <mat-label>Se vend et se compte</mat-label>
          <mat-select [value]="unite()" (valueChange)="unite.set($event)">
            @for (u of unites; track u.valeur) {
              <mat-option [value]="u.valeur">{{ u.libelle }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline" class="w-40" subscriptSizing="dynamic">
          <mat-label>Prix HT par {{ symboleUnite() }}</mat-label>
          <input
            matInput
            type="number"
            inputmode="decimal"
            min="0"
            [ngModel]="prix()"
            (ngModelChange)="prix.set($event)"
          />
          <span matTextSuffix>F</span>
        </mat-form-field>
      </div>

      @if (erreur(); as message) {
        <p class="erreur" role="alert">{{ message }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Annuler</button>
      <button
        mat-flat-button
        type="button"
        [disabled]="!complet() || envoi()"
        (click)="enregistrer()"
      >
        {{ envoi() ? 'Enregistrement…' : 'Créer l’article' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .lu {
      display: flex;
      align-items: center;
      gap: var(--gs-esp-2);
      margin: 0 0 var(--gs-esp-4);
      color: var(--gs-encre-2);
      font-size: var(--gs-texte-md);
    }
    .erreur {
      margin: var(--gs-esp-4) 0 0;
      padding: var(--gs-esp-2) var(--gs-esp-3);
      border-radius: var(--gs-r-champ);
      background: var(--gs-danger-fond);
      color: var(--gs-danger);
      font-size: var(--gs-texte-sm);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreationRapideArticle implements OnInit {
  private readonly service = inject(Catalogue);
  private readonly fenetre =
    inject<MatDialogRef<CreationRapideArticle, ArticleCreeRapidement>>(MatDialogRef);
  protected readonly donnees = inject<DonneesCreationRapide>(MAT_DIALOG_DATA);

  protected readonly NOUVELLE = NOUVELLE;
  protected readonly unites = UNITES;

  protected readonly categories = signal<CategoryDto[]>([]);
  protected readonly designation = signal('');
  protected readonly codeArticle = signal(this.donnees.code);
  protected readonly idCategorie = signal<number | null>(null);
  protected readonly nouvelleCategorie = signal('');
  protected readonly unite = signal<UniteMesure>('PIECE');
  protected readonly prix = signal<number | null>(null);
  protected readonly envoi = signal(false);
  protected readonly erreur = signal<string | null>(null);

  protected readonly symboleUnite = computed(() => symbole({ uniteBase: this.unite() }));

  protected readonly complet = computed(
    () =>
      !!this.designation().trim() &&
      !!this.codeArticle().trim() &&
      this.prix() != null &&
      (this.idCategorie() === NOUVELLE
        ? !!this.nouvelleCategorie().trim()
        : this.idCategorie() != null),
  );

  ngOnInit(): void {
    this.service.categories().subscribe({
      next: (liste) => {
        this.categories.set(liste);
        // Sans categorie, on propose d'en creer une plutot qu'une liste vide.
        this.idCategorie.set(
          liste.length === 1 ? (liste[0].id ?? null) : liste.length ? null : NOUVELLE,
        );
      },
      error: () => this.idCategorie.set(NOUVELLE),
    });
  }

  protected enregistrer(): void {
    if (!this.complet() || this.envoi()) {
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    const code = this.donnees.code.trim();
    const codeArticle = this.codeArticle().trim();

    this.categorie()
      .pipe(
        switchMap((categorie) =>
          this.service.enregistrerArticle({
            codeArticle,
            designation: this.designation().trim(),
            prixUnitaireHt: Number(this.prix()),
            uniteBase: this.unite(),
            category: { id: categorie.id },
          }),
        ),
        // Le code lu se rattache a l'article : c'est lui que la douchette relira. Inutile quand
        // il sert deja de code a l'article, que le scan reconnait aussi.
        switchMap((article) =>
          code === codeArticle
            ? of({ article, codeRattache: true })
            : this.service.ajouterCode(article.id!, { code }).pipe(
                map(() => ({ article, codeRattache: true })),
                // L'article existe deja : on le rend quand meme, plutot que d'inviter a
                // recliquer et a creer un doublon. L'appelant dit que le code reste a rattacher.
                catchError(() => of({ article, codeRattache: false })),
              ),
        ),
      )
      .subscribe({
        next: (resultat) => this.fenetre.close(resultat),
        error: (echec: unknown) => {
          this.envoi.set(false);
          this.erreur.set(messageDErreur(echec, 'L’article n’a pas pu être créé.'));
        },
      });
  }

  /** La categorie choisie, ou celle qu'on cree au passage. */
  private categorie(): Observable<CategoryDto> {
    if (this.idCategorie() !== NOUVELLE) {
      return of({ id: this.idCategorie()! });
    }
    const nom = this.nouvelleCategorie().trim();
    return this.service
      .enregistrerCategorie({ codeCategorie: codeDeCategorie(nom), designation: nom })
      .pipe(
        // Si l'article echoue ensuite, un second essai reprend cette categorie au lieu d'en
        // creer une jumelle.
        tap((creee) => {
          this.categories.update((liste) => [...liste, creee]);
          this.idCategorie.set(creee.id ?? null);
        }),
      );
  }
}

/** « Matériaux de construction » → « MATERIAUX-DE-CONSTRUCTION » : lisible, et unique en pratique. */
function codeDeCategorie(nom: string): string {
  return (
    nom
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .toUpperCase()
      .replace(/[^A-Z0-9]+/g, '-')
      .replace(/^-|-$/g, '')
      .slice(0, 30) || 'CATEGORIE'
  );
}
