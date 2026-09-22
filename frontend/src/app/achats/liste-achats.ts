import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { A_RECEVOIR, Achats, BROUILLONS, CommandeFourDto } from './achats.service';
import type { Page } from '../noyau/api';

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
 * commande de temps en temps. Le bouton pour en passer une reste visible dans les deux.
 */
@Component({
  selector: 'app-liste-achats',
  imports: [
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
  ],
  templateUrl: './liste-achats.html',
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

  protected readonly brouillons = computed(() => this.vue() === 'brouillons');

  constructor() {
    const vue = this.route.snapshot.queryParamMap.get('vue');
    if (vue === 'brouillons' || vue === 'attendues') {
      this.vue.set(vue);
    }
    this.charger();

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
  }

  private echouer(): void {
    this.chargement.set(false);
    this.erreur.set('Les commandes n’ont pas pu être chargées.');
  }
}
