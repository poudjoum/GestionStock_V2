import { Component, ElementRef, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { forkJoin } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import type { components } from '../api/schema';
import { Entreprise } from '../noyau/entreprise';
import { Sites } from '../noyau/sites';
import { messageDErreur } from '../noyau/erreurs';
import { imprimerEnA4 } from '../noyau/impression';
import { ecart, iso, variation } from './periodes';
import { Courbe, EnTetePage, OptionSelecteur, Selecteur, SerieCourbe } from '../design';

type RapportVentesDto = components['schemas']['RapportVentesDto'];
type RapportPertesDto = components['schemas']['RapportPertesDto'];
type RapportComptableDto = components['schemas']['RapportComptableDto'];

const API = `${environnement.api}/gestiondestock/v1/rapports`;

interface Rapports {
  ventes: RapportVentesDto;
  pertes: RapportPertesDto;
  compta: RapportComptableDto;
}

/**
 * Le rapport du mois : un document A4 a garder, a envoyer ou a presenter.
 *
 * Il reprend en deux pages ce que les trois ecrans de rapports disent en detail : les ventes et la
 * marge, ou le commerce perd, la TVA et les encaissements. A l'ecran il se lit comme la feuille qu'il
 * sera ; « Imprimer » le sort tel quel, ou en PDF si l'on choisit « Enregistrer en PDF » comme
 * imprimante — le nom du fichier est deja le bon.
 */
@Component({
  selector: 'app-rapport-du-mois',
  imports: [DecimalPipe, MatButtonModule, MatIconModule, Courbe, EnTetePage, Selecteur],
  templateUrl: './mois.html',
  styleUrl: './mois.css',
})
export class RapportDuMois implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly sites = inject(Sites);
  private readonly entreprise = inject(Entreprise);
  private readonly feuille = viewChild<ElementRef<HTMLElement>>('feuille');

  protected readonly rapports = signal<Rapports | null>(null);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);
  /** Le premier jour du mois affiche. Le mois dernier d'abord : c'est celui qu'on clot. */
  protected readonly mois = signal(premierDuMois(new Date(), -1));
  protected readonly tousSites = signal(false);
  protected readonly commerce = this.entreprise.mienne;

  protected readonly peutVoirTout = computed(() => this.sites.tousLesSites() && this.sites.plusieurs());
  protected readonly portee = computed<'site' | 'tous'>(() => (this.tousSites() ? 'tous' : 'site'));
  protected readonly portees = computed<OptionSelecteur<'site' | 'tous'>[]>(() => [
    { valeur: 'site', libelle: this.sites.actif()?.nom ?? 'Ce site' },
    { valeur: 'tous', libelle: 'Tous les sites' },
  ]);
  protected readonly nomDuMois = computed(() =>
    this.mois().toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' }),
  );
  /** On ne fait pas le rapport d'un mois qui n'a pas commence. */
  protected readonly dernierMois = computed(() => this.mois() >= premierDuMois(new Date(), 0));
  protected readonly edition = new Date().toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });
  protected readonly variation = variation;
  protected readonly ecart = ecart;

  protected readonly series = computed<SerieCourbe[]>(() => {
    const v = this.rapports()?.ventes;
    if (!v) return [];
    return [
      { nom: 'Chiffre d’affaires', valeurs: (v.serie ?? []).map((p) => p.chiffreAffaires ?? 0), couleur: 'var(--gs-serie-1)' },
      { nom: 'Marge', valeurs: (v.serie ?? []).map((p) => p.marge ?? 0), couleur: 'var(--gs-serie-2)' },
      { nom: 'Mois précédent', valeurs: (v.seriePrecedente ?? []).map((p) => p.chiffreAffaires ?? 0), couleur: 'var(--gs-serie-reference)', pointilles: true },
    ];
  });
  protected readonly jours = computed(() => (this.rapports()?.ventes.serie ?? []).map((p) => String(Number((p.cle ?? '').slice(8)))));
  protected readonly joursLongs = computed(() =>
    (this.rapports()?.ventes.serie ?? []).map((p) =>
      new Date(`${p.cle}T12:00:00`).toLocaleDateString('fr-FR', { weekday: 'short', day: 'numeric', month: 'short' }),
    ),
  );
  protected readonly impayesAnciens = computed(() =>
    (this.rapports()?.pertes.impayes?.parAnciennete ?? []).slice(2).reduce((s, t) => s + (t.montant ?? 0), 0),
  );

  ngOnInit(): void {
    this.entreprise.charger().subscribe({ error: () => undefined });
    this.charger();
  }

  protected changerDeMois(pas: number): void {
    this.mois.set(premierDuMois(this.mois(), pas));
    this.charger();
  }

  protected changerPortee(portee: 'site' | 'tous'): void {
    this.tousSites.set(portee === 'tous');
    this.charger();
  }

  protected imprimer(): void {
    const feuille = this.feuille()?.nativeElement;
    if (!feuille) return;
    const nom = (this.commerce()?.nom ?? 'Commerce').normalize('NFD').replace(/\p{M}/gu, '').replace(/[^A-Za-z0-9]+/g, '-');
    imprimerEnA4(feuille, `Rapport_${nom}_${iso(this.mois()).slice(0, 7)}`);
  }

  private charger(): void {
    const debut = this.mois();
    const fin = new Date(debut.getFullYear(), debut.getMonth() + 1, 0);
    const params = { debut: iso(debut), fin: iso(fin), tousSites: this.tousSites() };
    this.chargement.set(true);
    this.erreur.set(null);
    forkJoin({
      ventes: this.http.get<RapportVentesDto>(`${API}/ventes`, { params }),
      pertes: this.http.get<RapportPertesDto>(`${API}/pertes`, { params }),
      compta: this.http.get<RapportComptableDto>(`${API}/comptabilite`, { params }),
    }).subscribe({
      next: (rapports) => {
        this.rapports.set(rapports);
        this.chargement.set(false);
      },
      error: (echec: unknown) => {
        this.chargement.set(false);
        this.erreur.set(messageDErreur(echec, 'Le rapport du mois n’a pas pu être calculé.'));
      },
    });
  }
}

function premierDuMois(d: Date, decalage: number): Date {
  return new Date(d.getFullYear(), d.getMonth() + decalage, 1);
}
