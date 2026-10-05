import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { environnement } from '../../environnements/environnement';
import type { components } from '../api/schema';
import { Sites } from '../noyau/sites';
import { messageDErreur } from '../noyau/erreurs';
import { unites } from '../noyau/conditionnements';
import type { StatutAffiche } from '../noyau/statuts';
import { EnTetePage, EtatVide, OptionSelecteur, Section, Selecteur, Statut, Tuile } from '../design';

type AnalyseArticlesDto = components['schemas']['AnalyseArticlesDto'];
type LigneAnalyse = components['schemas']['LigneAnalyse'];
type Filtre = 'tout' | 'A' | 'B' | 'C' | 'dormants';

const RACINE = `${environnement.api}/gestiondestock/v1/analyses/articles`;

const CLASSES: Record<string, StatutAffiche> = {
  A: { libelle: 'A', ton: 'ok', icone: 'star' },
  B: { libelle: 'B', ton: 'neutre', icone: 'star_half' },
  C: { libelle: 'C', ton: 'neutre', icone: 'star_outline' },
};

/**
 * Ce que rapporte chaque article, et ce que le stock immobilise.
 *
 * Trois questions, dans l'ordre ou le gerant se les pose : qu'est-ce qui fait mon chiffre (les A,
 * a ne jamais laisser en rupture) ; qu'est-ce qui dort (du stock paye qui ne se vend pas) ; et
 * combien de jours tient chaque article au rythme actuel.
 */
@Component({
  selector: 'app-analyses',
  imports: [DatePipe, DecimalPipe, MatIconModule, EnTetePage, EtatVide, Section, Selecteur, Statut, Tuile],
  templateUrl: './analyses.html',
  styles: `
    .tuiles {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: var(--gs-esp-3);
      margin-bottom: var(--gs-esp-4);
    }
    .barre-part {
      display: block;
      height: 4px;
      margin-top: 4px;
      border-radius: 2px;
      background: var(--gs-accent, currentColor);
      opacity: 0.5;
    }
  `,
})
export class Analyses implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly sites = inject(Sites);

  protected readonly analyse = signal<AnalyseArticlesDto | null>(null);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);
  protected readonly jours = signal(90);
  protected readonly tousSites = signal(false);
  protected readonly filtre = signal<Filtre>('tout');

  protected readonly peutVoirTout = computed(() => this.sites.tousLesSites() && this.sites.plusieurs());
  protected readonly portee = computed<'site' | 'tous'>(() => (this.tousSites() ? 'tous' : 'site'));
  protected readonly portees = computed<OptionSelecteur<'site' | 'tous'>[]>(() => [
    { valeur: 'site', libelle: this.sites.actif()?.nom ?? 'Ce site' },
    { valeur: 'tous', libelle: 'Tous les sites' },
  ]);
  protected readonly periodes: OptionSelecteur<number>[] = [
    { valeur: 30, libelle: '30 jours' },
    { valeur: 90, libelle: '3 mois' },
    { valeur: 365, libelle: '1 an' },
  ];
  protected readonly filtres = computed<OptionSelecteur<Filtre>[]>(() => {
    const a = this.analyse();
    return [
      { valeur: 'tout', libelle: 'Tout' },
      { valeur: 'A', libelle: 'A', compteur: a?.nombreA ?? null },
      { valeur: 'B', libelle: 'B', compteur: a?.nombreB ?? null },
      { valeur: 'C', libelle: 'C', compteur: a?.nombreC ?? null },
      { valeur: 'dormants', libelle: 'Dormants', compteur: a?.nombreDormants ?? null },
    ];
  });
  protected readonly lignes = computed(() => {
    const toutes = this.analyse()?.articles ?? [];
    const f = this.filtre();
    if (f === 'tout') return toutes;
    if (f === 'dormants') return toutes.filter((l) => l.dormant);
    return toutes.filter((l) => l.classe === f);
  });
  /** La part la plus forte, pour mettre les barres a l'echelle. */
  protected readonly partMax = computed(() =>
    Math.max(1, ...(this.analyse()?.articles ?? []).map((l) => l.part ?? 0)),
  );

  protected readonly unites = unites;

  ngOnInit(): void {
    this.charger();
  }

  protected changerPeriode(jours: number): void {
    this.jours.set(jours);
    this.charger();
  }

  protected changerPortee(portee: 'site' | 'tous'): void {
    this.tousSites.set(portee === 'tous');
    this.charger();
  }

  protected classe(ligne: LigneAnalyse): StatutAffiche {
    return CLASSES[ligne.classe ?? 'C'];
  }

  private charger(): void {
    this.chargement.set(true);
    this.erreur.set(null);
    this.http
      .get<AnalyseArticlesDto>(RACINE, { params: { jours: this.jours(), tousSites: this.tousSites() } })
      .subscribe({
        next: (analyse) => {
          this.analyse.set(analyse);
          this.chargement.set(false);
        },
        error: (echec: unknown) => {
          this.chargement.set(false);
          this.erreur.set(messageDErreur(echec, 'L’analyse n’a pas pu être calculée.'));
        },
      });
  }
}
