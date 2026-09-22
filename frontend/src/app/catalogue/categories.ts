import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Catalogue, CategoryDto } from './catalogue.service';
import { Session } from '../noyau/session';
import { messageDErreur } from '../noyau/erreurs';

interface Saisie {
  id: number | null;
  codeCategorie: string;
  designation: string;
}

function vide(): Saisie {
  return { id: null, codeCategorie: '', designation: '' };
}

/**
 * Les categories, qui rangent les articles.
 *
 * Ecran volontairement plus simple que celui des articles : une categorie ne porte qu'un code et
 * un libelle, et l'on en cree cinq ou six une fois pour toutes. Lui donner un tableau a six
 * colonnes serait du decor.
 *
 * Il se filtre en local : la liste des rayons d'une maison tient sur un ecran, et une requete par
 * frappe serait du reseau depense pour rien.
 */
@Component({
  selector: 'app-categories',
  imports: [
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
  ],
  templateUrl: './categories.html',
})
export class Categories implements OnInit {
  private readonly service = inject(Catalogue);
  private readonly session = inject(Session);
  private readonly snack = inject(MatSnackBar);

  protected readonly toutes = signal<CategoryDto[]>([]);
  protected readonly recherche = signal('');
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);

  protected readonly volet = signal(false);
  protected readonly saisie = signal<Saisie>(vide());
  protected readonly envoiEnCours = signal(false);
  protected readonly erreurVolet = signal<string | null>(null);

  protected readonly modification = computed(() => this.saisie().id !== null);
  protected readonly complet = computed(() => !!this.saisie().codeCategorie.trim());
  protected readonly peutSupprimer = computed(() =>
    this.session.roles().some((r) => ['ROLE_ADMIN', 'ROLE_MANAGER'].includes(r)),
  );

  protected readonly lignes = computed(() => {
    const q = this.recherche().trim().toLowerCase();
    if (!q) {
      return this.toutes();
    }
    return this.toutes().filter((c) =>
      [c.codeCategorie, c.designation].some((champ) => champ?.toLowerCase().includes(q)),
    );
  });

  ngOnInit(): void {
    this.charger();
  }

  protected nouvelle(): void {
    this.saisie.set(vide());
    this.erreurVolet.set(null);
    this.volet.set(true);
  }

  protected modifier(categorie: CategoryDto): void {
    this.saisie.set({
      id: categorie.id ?? null,
      codeCategorie: categorie.codeCategorie ?? '',
      designation: categorie.designation ?? '',
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

    this.service
      .enregistrerCategorie({
        id: s.id ?? undefined,
        codeCategorie: s.codeCategorie.trim(),
        designation: s.designation.trim() || s.codeCategorie.trim(),
      })
      .subscribe({
        next: () => {
          this.envoiEnCours.set(false);
          const creation = s.id === null;
          this.snack.open(creation ? 'Catégorie créée.' : 'Catégorie modifiée.', 'Fermer', {
            duration: 3000,
          });
          this.charger();
          // Comme pour les articles : on en cree rarement une seule.
          if (creation) {
            this.saisie.set(vide());
          } else {
            this.fermer();
          }
        },
        error: (echec: unknown) => {
          this.envoiEnCours.set(false);
          this.erreurVolet.set(messageDErreur(echec, "La catégorie n'a pas pu être enregistrée."));
        },
      });
  }

  protected supprimer(): void {
    const s = this.saisie();
    if (s.id === null) {
      return;
    }
    // Une categorie qui range des articles ne se supprime pas : la base le refuse, et c'est son
    // message qu'on montre.
    this.service.supprimerCategorie(s.id).subscribe({
      next: () => {
        this.snack.open('Catégorie supprimée.', 'Fermer', { duration: 3000 });
        this.fermer();
        this.charger();
      },
      error: (echec: unknown) =>
        this.erreurVolet.set(
          messageDErreur(
            echec,
            'Cette catégorie ne peut pas être supprimée : des articles y sont rangés.',
          ),
        ),
    });
  }

  private charger(): void {
    this.chargement.set(true);
    this.erreur.set(null);
    this.service.categories().subscribe({
      next: (liste) => {
        this.toutes.set(liste);
        this.chargement.set(false);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Les catégories n’ont pas pu être chargées.');
      },
    });
  }
}
