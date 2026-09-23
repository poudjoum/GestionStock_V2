import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ArticleDto, Catalogue, CategoryDto } from './catalogue.service';
import { Session } from '../noyau/session';
import { messageDErreur } from '../noyau/erreurs';
import type { Page } from '../noyau/api';

/** Le formulaire, a plat : les champs que l'API accepte, et rien d'autre. */
interface Saisie {
  id: number | null;
  codeArticle: string;
  designation: string;
  prixUnitaireHt: number | null;
  tauxTva: number | null;
  seuilAlerte: number | null;
  idCategory: number | null;
}

function vide(): Saisie {
  return {
    id: null,
    codeArticle: '',
    designation: '',
    prixUnitaireHt: null,
    tauxTva: null,
    seuilAlerte: null,
    idCategory: null,
  };
}

/**
 * Le catalogue des articles.
 *
 * Ecran de bureau : on y entre des produits par lots, assis, rarement un seul. D'ou deux partis
 * pris.
 *
 * **La recherche precede la creation.** Le code d'un article est unique, et le risque quand on
 * saisit vingt references d'affilee est le doublon — pas la faute de frappe. Le champ de
 * recherche est donc en haut, avant le bouton de creation : on verifie qu'il n'existe pas avant
 * de l'ajouter.
 *
 * **Le volet reste ouvert apres l'enregistrement.** Il se vide et rend le focus au code : on
 * enchaine la reference suivante sans reouvrir quoi que ce soit. Refermer apres chaque
 * enregistrement obligerait a vingt clics pour vingt articles.
 */
@Component({
  selector: 'app-articles',
  imports: [
    DecimalPipe,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './articles.html',
})
export class Articles implements OnInit {
  private readonly service = inject(Catalogue);
  private readonly session = inject(Session);
  private readonly snack = inject(MatSnackBar);
  private readonly frappe = new Subject<string>();

  protected readonly recherche = signal('');
  protected readonly categorieFiltre = signal<number | null>(null);
  protected readonly lignes = signal<ArticleDto[]>([]);
  protected readonly total = signal(0);
  protected readonly categories = signal<CategoryDto[]>([]);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);

  protected readonly volet = signal(false);
  protected readonly saisie = signal<Saisie>(vide());
  protected readonly envoiEnCours = signal(false);
  protected readonly erreurVolet = signal<string | null>(null);

  protected readonly modification = computed(() => this.saisie().id !== null);
  protected readonly peutSupprimer = computed(() =>
    this.session.roles().some((r) => ['ROLE_ADMIN', 'ROLE_MANAGER'].includes(r)),
  );
  /**
   * Memes roles que la suppression, et pour la meme raison : un import reecrit les prix de tout
   * le magasin. Le magasinier tient la marchandise, pas la politique de prix.
   */
  protected readonly peutImporter = this.peutSupprimer;

  /** Le prix TTC se calcule sous les yeux : c'est celui qu'on annonce au client. */
  protected readonly prixTtc = computed(() => {
    const s = this.saisie();
    if (s.prixUnitaireHt == null) {
      return null;
    }
    return s.prixUnitaireHt * (1 + (s.tauxTva ?? 0) / 100);
  });

  protected readonly complet = computed(() => {
    const s = this.saisie();
    return !!s.codeArticle.trim() && !!s.designation.trim() && s.prixUnitaireHt != null && s.idCategory != null;
  });

  constructor() {
    this.frappe
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((q) => this.service.articles(q, this.categorieFiltre())),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (page) => this.afficher(page),
        error: () => this.echouer(),
      });
  }

  ngOnInit(): void {
    this.charger();
    this.chargerLesCategories();
  }

  protected chercher(q: string): void {
    this.recherche.set(q);
    this.chargement.set(true);
    this.frappe.next(q);
  }

  protected filtrerParCategorie(id: number | null): void {
    this.categorieFiltre.set(id);
    this.charger();
  }

  protected nomDeLaCategorie(article: ArticleDto): string {
    return article.category?.designation ?? '—';
  }

  // --- Le volet de saisie -------------------------------------------------------------------

  protected nouveau(): void {
    // La categorie filtree est pre-remplie : on entre en general plusieurs articles de la meme.
    this.saisie.set({ ...vide(), idCategory: this.categorieFiltre() });
    this.erreurVolet.set(null);
    this.volet.set(true);
  }

  protected modifier(article: ArticleDto): void {
    this.saisie.set({
      id: article.id ?? null,
      codeArticle: article.codeArticle ?? '',
      designation: article.designation ?? '',
      prixUnitaireHt: article.prixUnitaireHt ?? null,
      tauxTva: article.tauxTva ?? null,
      seuilAlerte: article.seuilAlerte ?? null,
      idCategory: article.category?.id ?? null,
    });
    this.erreurVolet.set(null);
    this.volet.set(true);
  }

  protected fermer(): void {
    this.volet.set(false);
    this.erreurVolet.set(null);
  }

  protected champ<K extends keyof Saisie>(cle: K, valeur: Saisie[K]): void {
    this.saisie.update((s) => ({ ...s, [cle]: valeur }));
  }

  protected enregistrer(): void {
    if (this.envoiEnCours() || !this.complet()) {
      return;
    }
    const s = this.saisie();
    this.envoiEnCours.set(true);
    this.erreurVolet.set(null);

    const article: ArticleDto = {
      id: s.id ?? undefined,
      codeArticle: s.codeArticle.trim(),
      designation: s.designation.trim(),
      prixUnitaireHt: s.prixUnitaireHt!,
      tauxTva: s.tauxTva ?? undefined,
      seuilAlerte: s.seuilAlerte ?? undefined,
      category: { id: s.idCategory! },
    };

    this.service.enregistrerArticle(article).subscribe({
      next: (enregistre) => {
        this.envoiEnCours.set(false);
        const creation = s.id === null;
        this.snack.open(
          creation ? `« ${enregistre.designation} » ajouté au catalogue.` : 'Article modifié.',
          'Fermer',
          { duration: 3000 },
        );
        this.charger();
        if (creation) {
          // On enchaine : le volet se vide et garde la categorie, puisqu'on saisit en general
          // plusieurs articles du meme rayon.
          this.saisie.set({ ...vide(), idCategory: s.idCategory });
        } else {
          this.fermer();
        }
      },
      error: (echec: unknown) => {
        this.envoiEnCours.set(false);
        // Le message de l'API dit quel champ manque, ou qu'un code existe deja.
        this.erreurVolet.set(messageDErreur(echec, "L'article n'a pas pu être enregistré."));
      },
    });
  }

  protected supprimer(article: ArticleDto): void {
    // Un article qui a bouge en stock ou figure sur une vente ne se supprime pas : la base le
    // refuse, et c'est son message qu'on montre plutot qu'un texte invente.
    this.service.supprimerArticle(article.id!).subscribe({
      next: () => {
        this.snack.open('Article supprimé.', 'Fermer', { duration: 3000 });
        this.fermer();
        this.charger();
      },
      error: (echec: unknown) =>
        this.erreurVolet.set(
          messageDErreur(
            echec,
            "Cet article ne peut pas être supprimé : il est utilisé par des mouvements ou des ventes.",
          ),
        ),
    });
  }

  // --- Chargement ---------------------------------------------------------------------------

  private charger(): void {
    this.chargement.set(true);
    this.erreur.set(null);
    this.service.articles(this.recherche(), this.categorieFiltre()).subscribe({
      next: (page) => this.afficher(page),
      error: () => this.echouer(),
    });
  }

  private chargerLesCategories(): void {
    this.service.categories().subscribe({
      next: (liste) => this.categories.set(liste),
      error: () => this.categories.set([]),
    });
  }

  private afficher(page: Page<ArticleDto>): void {
    this.lignes.set(page.content ?? []);
    this.total.set(page.totalElements ?? 0);
    this.chargement.set(false);
  }

  private echouer(): void {
    this.chargement.set(false);
    this.erreur.set('Le catalogue n’a pas pu être chargé.');
  }
}
