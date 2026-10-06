import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { environnement } from '../../environnements/environnement';
import type { components } from '../api/schema';
import { Sites } from '../noyau/sites';
import { messageDErreur } from '../noyau/erreurs';
import { Preset, PRESETS, bornes, iso, jourLisible } from './periodes';
import { Barre, Barres, EnTetePage, EtatVide, OptionSelecteur, Section, Selecteur } from '../design';

type RapportComptableDto = components['schemas']['RapportComptableDto'];
type Journal = 'ventes' | 'encaissements';

const RACINE = `${environnement.api}/gestiondestock/v1/rapports/comptabilite`;
/** Au-dela, le journal se lit dans le classeur : un ecran de 3 000 lignes ne se parcourt pas. */
const LIGNES_A_L_ECRAN = 100;
const MODES: Record<string, string> = {
  ESPECES: 'Espèces',
  MOBILE_MONEY: 'Mobile money',
  VIREMENT: 'Virement',
  CHEQUE: 'Chèque',
  BON_ACHAT: 'Bon d’achat',
  AUTRE: 'Autre',
};

/**
 * La comptabilite du mois : la TVA a declarer, les journaux, et le classeur pour le comptable.
 *
 * Ecran de bureau, celui du comptable et du gerant qui prepare sa declaration. Il s'ouvre sur le
 * mois dernier — celui qu'on clot. Les montants sont au centime : ils se rapprochent des factures.
 */
@Component({
  selector: 'app-comptabilite',
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    Barres,
    EnTetePage,
    EtatVide,
    Section,
    Selecteur,
  ],
  templateUrl: './comptabilite.html',
  styleUrl: './rapports.css',
})
export class Comptabilite implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly sites = inject(Sites);
  private readonly snack = inject(MatSnackBar);

  protected readonly rapport = signal<RapportComptableDto | null>(null);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);
  protected readonly telechargement = signal(false);
  protected readonly preset = signal<Preset>('mois-dernier');
  protected readonly debut = signal('');
  protected readonly fin = signal('');
  protected readonly tousSites = signal(false);
  protected readonly journal = signal<Journal>('ventes');

  protected readonly presets = PRESETS;
  protected readonly lignesAlEcran = LIGNES_A_L_ECRAN;
  protected readonly peutVoirTout = computed(() => this.sites.tousLesSites() && this.sites.plusieurs());
  protected readonly portee = computed<'site' | 'tous'>(() => (this.tousSites() ? 'tous' : 'site'));
  protected readonly portees = computed<OptionSelecteur<'site' | 'tous'>[]>(() => [
    { valeur: 'site', libelle: this.sites.actif()?.nom ?? 'Ce site' },
    { valeur: 'tous', libelle: 'Tous les sites' },
  ]);
  protected readonly journaux = computed<OptionSelecteur<Journal>[]>(() => [
    { valeur: 'ventes', libelle: 'Journal des ventes', compteur: this.rapport()?.journalVentes?.length ?? null },
    { valeur: 'encaissements', libelle: 'Encaissements', compteur: this.rapport()?.journalEncaissements?.length ?? null },
  ]);

  protected readonly periode = computed(() => {
    const r = this.rapport();
    return r ? `${jourLisible(r.debut)} – ${jourLisible(r.fin)}` : '';
  });
  protected readonly parMode = computed<Barre[]>(() =>
    (this.rapport()?.encaissementsParMode ?? []).map((m) => ({
      libelle: m.libelle ?? m.mode ?? '',
      valeur: m.montant ?? 0,
      detail: `${m.nombre} règlement${(m.nombre ?? 0) > 1 ? 's' : ''}`,
    })),
  );
  protected readonly ventes = computed(() => (this.rapport()?.journalVentes ?? []).slice(0, LIGNES_A_L_ECRAN));
  protected readonly encaissements = computed(() => (this.rapport()?.journalEncaissements ?? []).slice(0, LIGNES_A_L_ECRAN));

  ngOnInit(): void {
    const [debut, fin] = bornes('mois-dernier', new Date());
    this.debut.set(iso(debut));
    this.fin.set(iso(fin));
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

  protected mode(code: string | undefined): string {
    return code ? (MODES[code] ?? code) : '';
  }

  /** Le classeur, nomme par le serveur : le commerce et la periode. */
  protected telecharger(): void {
    if (this.telechargement()) return;
    this.telechargement.set(true);
    this.http
      .get(`${RACINE}.xlsx`, { params: this.params(), responseType: 'blob', observe: 'response' })
      .subscribe({
        next: (reponse) => {
          this.telechargement.set(false);
          const disposition = reponse.headers.get('content-disposition') ?? '';
          const encode = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1];
          const simple = /filename="?([^";]+)"?/i.exec(disposition)?.[1];
          const nom = encode ? decodeURIComponent(encode) : (simple ?? 'comptabilite.xlsx');
          const url = URL.createObjectURL(reponse.body!);
          const lien = document.createElement('a');
          lien.href = url;
          lien.download = nom;
          lien.click();
          setTimeout(() => URL.revokeObjectURL(url), 1000);
          this.snack.open(`Classeur téléchargé : ${nom}`, 'Fermer', { duration: 4000 });
        },
        error: (echec: unknown) => {
          this.telechargement.set(false);
          this.erreur.set(messageDErreur(echec, 'Le classeur n’a pas pu être préparé.'));
        },
      });
  }

  private params() {
    return { debut: this.debut(), fin: this.fin(), tousSites: this.tousSites() };
  }

  private charger(): void {
    if (this.debut() > this.fin()) {
      this.erreur.set('La date de début est après la date de fin.');
      return;
    }
    this.chargement.set(true);
    this.erreur.set(null);
    this.http.get<RapportComptableDto>(RACINE, { params: this.params() }).subscribe({
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
