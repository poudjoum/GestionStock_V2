import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, debounceTime, distinctUntilChanged, filter, startWith, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Genre, Repertoire as ServiceRepertoire, Tiers } from './repertoire.service';
import { Session } from '../noyau/session';
import { messageDErreur } from '../noyau/erreurs';
import type { Page } from '../noyau/api';

interface Saisie extends Tiers {
  ville: string;
  rue: string;
  pays: string;
}

function vide(): Saisie {
  return { nom: '', prenom: '', mail: '', tel: '', ville: '', rue: '', pays: '' };
}

/**
 * Le repertoire : les clients et les fournisseurs.
 *
 * Un seul ecran pour les deux, parce qu'ils ont exactement la meme forme — un nom, un prenom, un
 * courriel, un numero. Deux ecrans identiques finiraient par diverger, et l'un des deux aurait un
 * champ que l'autre n'a pas sans que personne sache pourquoi.
 *
 * Mais deux metiers, et c'est ce qui decide de la bascule : le caissier enregistre un client qui
 * se presente au comptoir, le magasinier un fournisseur qui livre. L'API leur ouvre des droits
 * differents — le caissier ne cree pas de fournisseur — et l'ecran ne montre donc que l'onglet
 * qu'on peut reellement utiliser.
 */
@Component({
  selector: 'app-repertoire',
  imports: [
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
  ],
  templateUrl: './repertoire.html',
})
export class RepertoireEcran {
  private readonly service = inject(ServiceRepertoire);
  private readonly session = inject(Session);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);
  private readonly frappe = new Subject<string>();

  protected readonly genre = signal<Genre>('client');
  protected readonly recherche = signal('');
  protected readonly lignes = signal<Tiers[]>([]);
  protected readonly total = signal(0);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);

  protected readonly volet = signal(false);
  protected readonly saisie = signal<Saisie>(vide());
  protected readonly envoiEnCours = signal(false);
  protected readonly erreurVolet = signal<string | null>(null);

  protected readonly modification = computed(() => this.saisie().id != null);
  protected readonly peutSupprimer = computed(() =>
    this.session.roles().some((r) => ['ROLE_ADMIN', 'ROLE_MANAGER'].includes(r)),
  );

  /** Les quatre champs que l'API exige, sans exception. */
  protected readonly complet = computed(() => {
    const s = this.saisie();
    return !!s.nom.trim() && !!s.prenom.trim() && !!s.mail.trim() && !!s.tel.trim();
  });

  // Les droits ne sont pas les memes : le caissier cree des clients, le magasinier des
  // fournisseurs. Montrer un onglet qui rendra 403 serait une promesse qu'on ne tient pas.
  protected readonly peutVoirClients = computed(() =>
    this.session.roles().some((r) => ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_CAISSIER'].includes(r)),
  );
  protected readonly peutVoirFournisseurs = computed(() =>
    this.session.roles().some((r) => ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER'].includes(r)),
  );
  protected readonly bascule = computed(() => this.peutVoirClients() && this.peutVoirFournisseurs());

  protected readonly libelle = computed(() =>
    this.genre() === 'client' ? 'client' : 'fournisseur',
  );

  constructor() {
    // Le genre vient de l'URL : /clients et /fournisseurs sont deux adresses, pour qu'un lien ou
    // un signet mene a l'onglet attendu.
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        startWith(null),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        const vise: Genre = this.router.url.startsWith('/fournisseurs') ? 'fournisseur' : 'client';
        if (vise !== this.genre() || this.lignes().length === 0) {
          this.genre.set(vise);
          this.recherche.set('');
          this.fermer();
          this.charger();
        }
      });

    this.frappe
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((q) => this.service.lister(this.genre(), q)),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (page) => this.afficher(page),
        error: () => this.echouer(),
      });
  }

  protected changerGenre(genre: Genre): void {
    void this.router.navigate([genre === 'client' ? '/clients' : '/fournisseurs']);
  }

  protected chercher(q: string): void {
    this.recherche.set(q);
    this.chargement.set(true);
    this.frappe.next(q);
  }

  protected nomComplet(t: Tiers): string {
    return [t.nom, t.prenom].filter(Boolean).join(' ');
  }

  protected initiales(t: Tiers): string {
    return ((t.nom?.[0] ?? '') + (t.prenom?.[0] ?? '')).toUpperCase() || '?';
  }

  // --- Le volet -----------------------------------------------------------------------------

  protected nouveau(): void {
    this.saisie.set(vide());
    this.erreurVolet.set(null);
    this.volet.set(true);
  }

  protected modifier(t: Tiers): void {
    this.saisie.set({
      ...t,
      ville: t.adresse?.ville ?? '',
      rue: t.adresse?.adresse1 ?? '',
      pays: t.adresse?.pays ?? '',
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

    const tiers: Tiers = {
      id: s.id,
      nom: s.nom.trim(),
      prenom: s.prenom.trim(),
      mail: s.mail.trim(),
      tel: s.tel.trim(),
      // Les champs d'adresse qu'on ne montre pas — complement, code postal — sont conserves tels
      // quels : ne pas les afficher n'est pas une raison de les effacer.
      adresse: {
        ...s.adresse,
        adresse1: s.rue.trim() || undefined,
        ville: s.ville.trim() || undefined,
        pays: s.pays.trim() || undefined,
      },
    };

    this.service.enregistrer(this.genre(), tiers).subscribe({
      next: (enregistre) => {
        this.envoiEnCours.set(false);
        const creation = s.id == null;
        this.snack.open(
          creation
            ? `${this.nomComplet(enregistre)} ajouté aux ${this.libelle()}s.`
            : 'Fiche modifiée.',
          'Fermer',
          { duration: 3000 },
        );
        this.charger();
        if (creation) {
          this.saisie.set(vide());
        } else {
          this.fermer();
        }
      },
      error: (echec: unknown) => {
        this.envoiEnCours.set(false);
        this.erreurVolet.set(messageDErreur(echec, "La fiche n'a pas pu être enregistrée."));
      },
    });
  }

  protected supprimer(): void {
    const s = this.saisie();
    if (s.id == null) {
      return;
    }
    this.service.supprimer(this.genre(), s.id).subscribe({
      next: () => {
        this.snack.open('Fiche supprimée.', 'Fermer', { duration: 3000 });
        this.fermer();
        this.charger();
      },
      error: (echec: unknown) =>
        // Un client qui a des ventes, un fournisseur qui a des commandes : la base refuse, et
        // c'est son message qu'on montre.
        this.erreurVolet.set(
          messageDErreur(
            echec,
            `Ce ${this.libelle()} ne peut pas être supprimé : des documents y font référence.`,
          ),
        ),
    });
  }

  // --- Chargement ---------------------------------------------------------------------------

  private charger(): void {
    this.chargement.set(true);
    this.erreur.set(null);
    this.service.lister(this.genre(), this.recherche()).subscribe({
      next: (page) => this.afficher(page),
      error: () => this.echouer(),
    });
  }

  private afficher(page: Page<Tiers>): void {
    this.lignes.set(page.content ?? []);
    this.total.set(page.totalElements ?? 0);
    this.chargement.set(false);
  }

  private echouer(): void {
    this.chargement.set(false);
    this.erreur.set(`Les ${this.libelle()}s n’ont pas pu être chargés.`);
  }
}
