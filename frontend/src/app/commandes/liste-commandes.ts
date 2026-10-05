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
import {
  A_SERVIR,
  CommandeClientDto,
  CommandesClients,
  EN_PREPARATION,
  TERMINEES,
} from './commandes.service';
import { statutDeCommandeClient } from './statuts';
import type { Page } from '../noyau/api';
import { EnTetePage, EtatVide, OptionSelecteur, Section, Selecteur, Statut } from '../design';

type Vue = 'a-servir' | 'preparation' | 'terminees';

const ETATS: Record<Vue, typeof A_SERVIR> = {
  'a-servir': A_SERVIR,
  preparation: EN_PREPARATION,
  terminees: TERMINEES,
};

/**
 * Les commandes des clients : ce qu'on leur a promis, et ce qu'on prepare.
 *
 * L'onglet ouvert d'abord est « à servir » : c'est la marchandise reservee, et le client qui
 * attend. Les commandes terminees ne sont la que pour repondre a un client qui rappelle.
 */
@Component({
  selector: 'app-liste-commandes',
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
  templateUrl: './liste-commandes.html',
  styleUrl: '../achats/liste-achats.css',
})
export class ListeCommandes {
  private readonly service = inject(CommandesClients);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly frappe = new Subject<string>();

  protected readonly vue = signal<Vue>('a-servir');
  protected readonly recherche = signal('');
  protected readonly commandes = signal<CommandeClientDto[]>([]);
  protected readonly total = signal(0);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);
  protected readonly comptes = signal<Record<Vue, number | null>>({
    'a-servir': null,
    preparation: null,
    terminees: null,
  });

  protected readonly vues = computed<OptionSelecteur<Vue>[]>(() => [
    { valeur: 'a-servir', libelle: 'À servir', compteur: this.comptes()['a-servir'] },
    { valeur: 'preparation', libelle: 'En préparation', compteur: this.comptes().preparation },
    { valeur: 'terminees', libelle: 'Terminées' },
  ]);

  protected readonly statut = statutDeCommandeClient;

  constructor() {
    const vue = this.route.snapshot.queryParamMap.get('vue') as Vue | null;
    if (vue && vue in ETATS) {
      this.vue.set(vue);
      this.charger();
    } else {
      this.premierChargement();
    }

    this.frappe
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((q) => this.service.lister(ETATS[this.vue()], q)),
        takeUntilDestroyed(),
      )
      .subscribe({ next: (page) => this.afficher(page), error: () => this.echouer() });
  }

  protected changerVue(vue: Vue): void {
    if (vue === this.vue()) {
      return;
    }
    this.vue.set(vue);
    void this.router.navigate([], { relativeTo: this.route, queryParams: { vue }, replaceUrl: true });
    this.charger();
  }

  protected chercher(q: string): void {
    this.recherche.set(q);
    this.chargement.set(true);
    this.frappe.next(q);
  }

  protected nouvelle(): void {
    void this.router.navigate(['/commandes', 'nouvelle']);
  }

  protected ouvrir(commande: CommandeClientDto): void {
    void this.router.navigate(['/commandes', commande.id]);
  }

  protected nomDuClient(c: CommandeClientDto): string {
    return [c.client?.prenoms, c.client?.nom].filter(Boolean).join(' ') || 'Client';
  }

  /** L'entrepot qui livre se dit : c'est son equipe qui sert. */
  protected livreAilleurs(c: CommandeClientDto): boolean {
    return !!c.idSiteExpedition && c.idSiteExpedition !== c.idSite;
  }

  /** A l'arrivee, les deux premiers onglets d'un coup : leurs comptes, et le bon ouvert. */
  private premierChargement(): void {
    forkJoin({
      aServir: this.service.lister(A_SERVIR, ''),
      preparation: this.service.lister(EN_PREPARATION, ''),
    }).subscribe({
      next: ({ aServir, preparation }) => {
        this.comptes.update((c) => ({
          ...c,
          'a-servir': aServir.totalElements ?? 0,
          preparation: preparation.totalElements ?? 0,
        }));
        const surPreparation = !aServir.totalElements && !!preparation.totalElements;
        this.vue.set(surPreparation ? 'preparation' : 'a-servir');
        this.afficher(surPreparation ? preparation : aServir);
      },
      error: () => this.echouer(),
    });
  }

  private charger(): void {
    this.chargement.set(true);
    this.erreur.set(null);
    this.service.lister(ETATS[this.vue()], this.recherche()).subscribe({
      next: (page) => this.afficher(page),
      error: () => this.echouer(),
    });
  }

  private afficher(page: Page<CommandeClientDto>): void {
    this.commandes.set(page.content ?? []);
    this.total.set(page.totalElements ?? 0);
    this.chargement.set(false);
    if (!this.recherche() && this.vue() !== 'terminees') {
      this.comptes.update((c) => ({ ...c, [this.vue()]: page.totalElements ?? 0 }));
    }
  }

  private echouer(): void {
    this.chargement.set(false);
    this.erreur.set('Les commandes n’ont pas pu être chargées.');
  }
}
