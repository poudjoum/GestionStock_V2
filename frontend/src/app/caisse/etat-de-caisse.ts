import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { environnement } from '../../environnements/environnement';
import { libelleDuMode } from '../factures/factures.service';
import type { components } from '../api/schema';
import type { Page } from '../noyau/api';

type EtatDeCaisseDto = components['schemas']['EtatDeCaisseDto'];
type ReglementDto = components['schemas']['ReglementDto'];

const API = `${environnement.api}/gestiondestock/v1/caisse`;

/** Une date au format que l'API attend, dans le fuseau du poste. */
function enJour(date: Date): string {
  const mois = `${date.getMonth() + 1}`.padStart(2, '0');
  const jour = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${mois}-${jour}`;
}

function ilYA(jours: number): string {
  const date = new Date();
  date.setDate(date.getDate() - jours);
  return enJour(date);
}

/**
 * L'etat de caisse : ce qui est entre, et par quel moyen.
 *
 * Les periodes courantes sont des boutons plutot qu'un calendrier : « aujourd'hui », « la
 * semaine », « le mois » couvrent presque tous les cas, et obliger a choisir deux dates pour
 * savoir ce qu'a fait la journee serait deux clics de trop, tous les soirs.
 */
@Component({
  selector: 'app-etat-de-caisse',
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
  ],
  templateUrl: './etat-de-caisse.html',
})
export class EtatDeCaisse implements OnInit {
  private readonly http = inject(HttpClient);

  protected readonly debut = signal(enJour(new Date()));
  protected readonly fin = signal(enJour(new Date()));
  protected readonly periode = signal<'jour' | 'semaine' | 'mois' | 'libre'>('jour');

  protected readonly etat = signal<EtatDeCaisseDto | null>(null);
  protected readonly reglements = signal<ReglementDto[]>([]);
  protected readonly totalReglements = signal(0);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);

  protected readonly libelleDuMode = libelleDuMode;

  ngOnInit(): void {
    this.charger();
  }

  protected choisirPeriode(periode: 'jour' | 'semaine' | 'mois'): void {
    this.periode.set(periode);
    const aujourdhui = enJour(new Date());
    this.fin.set(aujourdhui);
    this.debut.set(periode === 'jour' ? aujourdhui : ilYA(periode === 'semaine' ? 6 : 29));
    this.charger();
  }

  protected changerDate(borne: 'debut' | 'fin', valeur: string): void {
    if (!valeur) {
      return;
    }
    this.periode.set('libre');
    (borne === 'debut' ? this.debut : this.fin).set(valeur);
    this.charger();
  }

  /** La part d'un mode dans le total, pour la barre qui l'accompagne. */
  protected part(total: number | undefined): number {
    const global = this.etat()?.total ?? 0;
    return global === 0 ? 0 : ((total ?? 0) / global) * 100;
  }

  protected charger(): void {
    this.chargement.set(true);
    this.erreur.set(null);
    const params = { debut: this.debut(), fin: this.fin() };

    this.http.get<EtatDeCaisseDto>(`${API}/etat`, { params }).subscribe({
      next: (etat) => {
        this.etat.set(etat);
        this.chargement.set(false);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('L’état de caisse n’a pas pu être chargé.');
      },
    });

    this.http
      .get<Page<ReglementDto>>(`${API}/reglements`, {
        params: { ...params, page: 0, size: 50, sort: 'dateReglement,desc' },
      })
      .subscribe({
        next: (page) => {
          this.reglements.set(page.content ?? []);
          this.totalReglements.set(page.totalElements ?? 0);
        },
        error: () => this.reglements.set([]),
      });
  }
}
