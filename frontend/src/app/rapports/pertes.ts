import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { environnement } from '../../environnements/environnement';
import type { components } from '../api/schema';
import { Sites } from '../noyau/sites';
import { messageDErreur } from '../noyau/erreurs';
import { Preset, PRESETS, bornes, debutDuMois, ecart, iso, jourLisible, variation } from './periodes';
import { Barre, Barres, Colonne, Colonnes, EnTetePage, EtatVide, OptionSelecteur, Section, Selecteur } from '../design';

type RapportPertesDto = components['schemas']['RapportPertesDto'];

const RACINE = `${environnement.api}/gestiondestock/v1/rapports/pertes`;

/**
 * Ou le commerce perd : ce qui sort sans etre vendu, ce que les clients doivent, ce qui dort.
 *
 * Ici une hausse est une mauvaise nouvelle : l'ecart de la demarque se lit a l'envers de celui du
 * chiffre d'affaires, et sa couleur aussi. La demarque suit la periode choisie ; les impayes et le
 * stock qui dort, eux, sont ceux d'aujourd'hui — l'ecran le dit.
 */
@Component({
  selector: 'app-pertes',
  imports: [
    DecimalPipe,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    Barres,
    Colonnes,
    EnTetePage,
    EtatVide,
    Section,
    Selecteur,
  ],
  templateUrl: './pertes.html',
  styleUrl: './rapports.css',
})
export class Pertes implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly sites = inject(Sites);

  protected readonly rapport = signal<RapportPertesDto | null>(null);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);
  protected readonly preset = signal<Preset>('mois');
  protected readonly debut = signal(iso(debutDuMois(new Date())));
  protected readonly fin = signal(iso(new Date()));
  protected readonly tousSites = signal(false);

  protected readonly presets = PRESETS;
  protected readonly variation = variation;
  protected readonly peutVoirTout = computed(() => this.sites.tousLesSites() && this.sites.plusieurs());
  protected readonly portee = computed<'site' | 'tous'>(() => (this.tousSites() ? 'tous' : 'site'));
  protected readonly portees = computed<OptionSelecteur<'site' | 'tous'>[]>(() => [
    { valeur: 'site', libelle: this.sites.actif()?.nom ?? 'Ce site' },
    { valeur: 'tous', libelle: 'Tous les sites' },
  ]);

  protected readonly periode = computed(() => {
    const r = this.rapport();
    return r ? `${jourLisible(r.debut)} – ${jourLisible(r.fin)}` : '';
  });

  protected readonly demarque = computed(() => this.rapport()?.demarque ?? null);
  protected readonly impayes = computed(() => this.rapport()?.impayes ?? null);
  protected readonly dormants = computed(() => this.rapport()?.dormants ?? null);

  /** Une hausse de la demarque est une mauvaise nouvelle. */
  protected readonly ecartDemarque = computed(() => ecart(this.demarque()?.valeur, this.demarque()?.valeurPrecedente));

  /** Ce qui est du depuis plus de 60 jours : la part qui risque de ne jamais rentrer. */
  protected readonly duAncien = computed(() =>
    (this.impayes()?.parAnciennete ?? []).slice(2).reduce((s, t) => s + (t.montant ?? 0), 0),
  );

  protected readonly parMotif = computed<Barre[]>(() =>
    (this.demarque()?.parMotif ?? [])
      .filter((p) => (p.valeur ?? 0) > 0)
      .map((p) => ({
        libelle: p.libelle ?? '',
        valeur: p.valeur ?? 0,
        detail: `${unites(p.quantite)} · ${p.mouvements} mouvement${(p.mouvements ?? 0) > 1 ? 's' : ''}`,
      })),
  );
  /** Le surplus d'inventaire vient en deduction : il ne se dessine pas, il se dit. */
  protected readonly surplus = computed(() =>
    (this.demarque()?.parMotif ?? []).find((p) => p.cle === 'INVENTAIRE_SURPLUS' && (p.valeur ?? 0) < 0) ?? null,
  );
  protected readonly parArticle = computed<Barre[]>(() =>
    (this.demarque()?.parArticle ?? []).map((p) => ({
      libelle: p.libelle ?? '',
      valeur: p.valeur ?? 0,
      detail: unites(p.quantite),
    })),
  );
  protected readonly tranches = computed<Colonne[]>(() =>
    (this.impayes()?.parAnciennete ?? []).map((t) => ({
      libelle: t.libelle ?? '',
      valeur: t.montant ?? 0,
      detail: `${t.factures} facture${(t.factures ?? 0) > 1 ? 's' : ''}`,
    })),
  );

  ngOnInit(): void {
    this.charger();
  }

  protected choisirPreset(preset: Preset): void {
    this.preset.set(preset);
    if (preset === 'libre') return;
    const [debut, fin] = bornes(preset, new Date());
    this.debut.set(iso(debut));
    this.fin.set(iso(fin));
    this.charger();
  }

  protected changerDates(debut: string, fin: string): void {
    if (!debut || !fin) return;
    this.debut.set(debut);
    this.fin.set(fin);
    this.charger();
  }

  protected changerPortee(portee: 'site' | 'tous'): void {
    this.tousSites.set(portee === 'tous');
    this.charger();
  }

  protected depuis(instant: string | undefined): string {
    return instant ? new Date(instant).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' }) : '';
  }

  private charger(): void {
    if (this.debut() > this.fin()) {
      this.erreur.set('La date de début est après la date de fin.');
      return;
    }
    this.chargement.set(true);
    this.erreur.set(null);
    this.http
      .get<RapportPertesDto>(RACINE, { params: { debut: this.debut(), fin: this.fin(), tousSites: this.tousSites() } })
      .subscribe({
        next: (rapport) => {
          this.rapport.set(rapport);
          this.chargement.set(false);
        },
        error: (echec: unknown) => {
          this.chargement.set(false);
          this.erreur.set(messageDErreur(echec, 'Le rapport n’a pas pu être calculé.'));
        },
      });
  }
}

/** « 1 unité », « 12 unités », « 1,5 unité » : le pluriel francais commence a 2. */
function unites(quantite: number | undefined): string {
  const q = quantite ?? 0;
  return `${q.toLocaleString('fr-FR')} unité${Math.abs(q) >= 2 ? 's' : ''}`;
}
