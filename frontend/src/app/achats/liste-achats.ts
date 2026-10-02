import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { Subject, debounceTime, distinctUntilChanged, forkJoin, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { A_RECEVOIR, Achats, BROUILLONS, CommandeFourDto } from './achats.service';
import type { Page } from '../noyau/api';
import { EnTetePage, EtatVide, OptionSelecteur, Section, Selecteur, Statut } from '../design';

/** Les deux moments d'une commande, et les deux seuls qui appellent un geste. */
type Vue = 'brouillons' | 'attendues';

/**
 * Les achats : ce qu'on prépare, et ce qu'on attend.
 *
 * Un seul écran pour les deux, parce que c'est une seule chose — une commande fournisseur qui
 * avance. La bascule sépare les deux moments où elle demande quelque chose : la composer, puis
 * recevoir ce qui arrive. Ce qui est livré, annulé ou clôturé n'y figure pas : c'est de
 * l'historique, et il n'y a rien à y faire.
 *
 * La vue par défaut est « à recevoir » : on décharge un camion tous les jours, on passe une
 * commande de temps en temps. Mais si rien n'est attendu et que des brouillons attendent, on ouvre
 * sur eux : un onglet vide à l'arrivée, avec deux commandes à côté, faisait croire qu'il n'y avait
 * rien. Chaque onglet porte son compte pour la même raison.
 */
@Component({
  selector: 'app-liste-achats',
  imports: [
    DatePipe,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    EnTetePage,
    EtatVide,
    Section,
    Selecteur,
    Statut,
  ],
  templateUrl: './liste-achats.html',
  styleUrl: './liste-achats.css',
})
export class ListeAchats {
  private readonly service = inject(Achats);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly frappe = new Subject<string>();

  protected readonly vue = signal<Vue>('attendues');
  protected readonly recherche = signal('');
  protected readonly commandes = signal<CommandeFourDto[]>([]);
  protected readonly total = signal(0);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);

  /** Le nombre de commandes de chaque onglet, sans recherche : ce qui attend un geste. */
  protected readonly comptes = signal<Record<Vue, number | null>>({ brouillons: null, attendues: null });

  protected readonly brouillons = computed(() => this.vue() === 'brouillons');

  protected readonly vues = computed<OptionSelecteur<Vue>[]>(() => [
    { valeur: 'attendues', libelle: 'À recevoir', compteur: this.comptes().attendues },
    { valeur: 'brouillons', libelle: 'En préparation', compteur: this.comptes().brouillons },
  ]);

  constructor() {
    const vue = this.route.snapshot.queryParamMap.get('vue');
    if (vue === 'brouillons' || vue === 'attendues') {
      this.vue.set(vue);
      this.charger();
    } else {
      this.premierChargement();
    }

    this.frappe
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((q) => this.service.lister(this.etats(), q)),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (page) => this.afficher(page),
        error: () => this.echouer(),
      });
  }

  protected changerVue(vue: Vue): void {
    if (vue === this.vue()) {
      return;
    }
    this.vue.set(vue);
    // La vue vit dans l'URL : revenir d'une commande retrouve l'onglet qu'on avait ouvert.
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { vue },
      replaceUrl: true,
    });
    this.charger();
  }

  protected chercher(q: string): void {
    this.recherche.set(q);
    this.chargement.set(true);
    this.frappe.next(q);
  }

  protected nouvelle(): void {
    void this.router.navigate(['/achats', 'nouvelle']);
  }

  /** Un brouillon se compose, une commande passée se reçoit : deux écrans, deux gestes. */
  protected ouvrir(commande: CommandeFourDto): void {
    void this.router.navigate(
      this.brouillons() ? ['/achats', commande.id] : ['/achats', commande.id, 'reception'],
    );
  }

  protected reliquat(commande: CommandeFourDto): boolean {
    return commande.etat === 'PARTIELLEMENT_LIVREE';
  }

  private etats() {
    return this.brouillons() ? BROUILLONS : A_RECEVOIR;
  }

  /** Les deux onglets d'un coup : leurs comptes, et le bon onglet ouvert d'emblée. */
  private premierChargement(): void {
    forkJoin({
      attendues: this.service.lister(A_RECEVOIR, ''),
      brouillons: this.service.lister(BROUILLONS, ''),
    }).subscribe({
      next: ({ attendues, brouillons }) => {
        this.comptes.set({
          attendues: attendues.totalElements ?? 0,
          brouillons: brouillons.totalElements ?? 0,
        });
        const surBrouillons = !attendues.totalElements && !!brouillons.totalElements;
        this.vue.set(surBrouillons ? 'brouillons' : 'attendues');
        this.afficher(surBrouillons ? brouillons : attendues);
      },
      error: () => this.echouer(),
    });
  }

  private charger(): void {
    this.chargement.set(true);
    this.erreur.set(null);
    this.service.lister(this.etats(), this.recherche()).subscribe({
      next: (page) => this.afficher(page),
      error: () => this.echouer(),
    });
  }

  private afficher(page: Page<CommandeFourDto>): void {
    this.commandes.set(page.content ?? []);
    this.total.set(page.totalElements ?? 0);
    this.chargement.set(false);
    // Sans recherche, le total de la page est le compte de l'onglet.
    if (!this.recherche()) {
      this.comptes.update((c) => ({ ...c, [this.vue()]: page.totalElements ?? 0 }));
    }
  }

  private echouer(): void {
    this.chargement.set(false);
    this.erreur.set('Les commandes n’ont pas pu être chargées.');
  }
}
