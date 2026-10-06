import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { environnement } from '../../environnements/environnement';
import type { components } from '../api/schema';
import { Sites } from '../noyau/sites';
import { messageDErreur } from '../noyau/erreurs';
import {
  Barre,
  Barres,
  Colonne,
  Colonnes,
  Courbe,
  EnTetePage,
  EtatVide,
  OptionSelecteur,
  Section,
  Selecteur,
  SerieCourbe,
} from '../design';

type RapportVentesDto = components['schemas']['RapportVentesDto'];
type Indicateurs = components['schemas']['Indicateurs'];
type Repartition = components['schemas']['Repartition'];
type Preset = 'jour' | 'semaine' | 'mois' | 'mois-dernier' | 'annee' | 'libre';

const RACINE = `${environnement.api}/gestiondestock/v1/rapports/ventes`;
const JOURS = ['lun.', 'mar.', 'mer.', 'jeu.', 'ven.', 'sam.', 'dim.'];
const JOURS_ENTIERS = ['lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi', 'dimanche'];

/** Une tuile : le chiffre, et comment il a bouge. */
interface Tuile {
  libelle: string;
  valeur: number;
  unite: string;
  detail: string | null;
  vsPrecedent: number | null;
  vsAnnee: number | null;
}

/**
 * Les rapports de ventes : est-ce que je gagne, et ca monte ou ca baisse ?
 *
 * Ecran de bureau, celui du gerant et du comptable. En haut, quatre chiffres et comment ils ont
 * bouge — contre la periode precedente de meme longueur, et contre la meme periode l'an dernier :
 * octobre se compare a septembre, mais aussi a octobre, qui n'a pas la meme saison. Puis la courbe,
 * et ou se fait le chiffre : rayon, magasin, vendeur, heure, jour.
 */
@Component({
  selector: 'app-rapports',
  imports: [
    DecimalPipe,
    FormsModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    Barres,
    Colonnes,
    Courbe,
    EnTetePage,
    EtatVide,
    Section,
    Selecteur,
  ],
  templateUrl: './rapports.html',
  styleUrl: './rapports.css',
})
export class Rapports implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly sites = inject(Sites);

  protected readonly rapport = signal<RapportVentesDto | null>(null);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);

  protected readonly preset = signal<Preset>('mois');
  protected readonly debut = signal(iso(debutDuMois(new Date())));
  protected readonly fin = signal(iso(new Date()));
  protected readonly tousSites = signal(false);

  protected readonly presets: OptionSelecteur<Preset>[] = [
    { valeur: 'jour', libelle: 'Aujourd’hui' },
    { valeur: 'semaine', libelle: '7 jours' },
    { valeur: 'mois', libelle: 'Ce mois' },
    { valeur: 'mois-dernier', libelle: 'Mois dernier' },
    { valeur: 'annee', libelle: 'Cette année' },
    { valeur: 'libre', libelle: 'Dates…' },
  ];
  protected readonly peutVoirTout = computed(() => this.sites.tousLesSites() && this.sites.plusieurs());
  protected readonly portee = computed<'site' | 'tous'>(() => (this.tousSites() ? 'tous' : 'site'));
  protected readonly portees = computed<OptionSelecteur<'site' | 'tous'>[]>(() => [
    { valeur: 'site', libelle: this.sites.actif()?.nom ?? 'Ce site' },
    { valeur: 'tous', libelle: 'Tous les sites' },
  ]);

  protected readonly vide = computed(() => (this.rapport()?.courant?.tickets ?? 0) === 0);

  protected readonly tuiles = computed<Tuile[]>(() => {
    const r = this.rapport();
    if (!r?.courant) return [];
    const c = r.courant;
    const p = r.precedent ?? {};
    const a = r.anneePrecedente ?? {};
    return [
      tuile('Chiffre d’affaires HT', c.chiffreAffaires, p.chiffreAffaires, a.chiffreAffaires, 'F', null),
      tuile('Marge', c.marge, p.marge, a.marge, 'F',
        c.tauxMarge != null ? `${c.tauxMarge.toLocaleString('fr-FR')} % du chiffre` : 'Aucun coût d’achat connu'),
      tuile('Tickets', c.tickets, p.tickets, a.tickets, '', null),
      tuile('Panier moyen', c.panierMoyen, p.panierMoyen, a.panierMoyen, 'F', null),
    ];
  });

  /** La marge ne couvre pas tout le chiffre : des articles n'ont jamais ete achetes par commande. */
  protected readonly couverture = computed(() => {
    const c = this.rapport()?.courant;
    if (!c?.chiffreAffaires || c.caCouvert == null || c.caCouvert >= c.chiffreAffaires) return null;
    return Math.round((c.caCouvert / c.chiffreAffaires) * 100);
  });

  protected readonly libelles = computed(() => (this.rapport()?.serie ?? []).map((p) => libelleLong(p.cle ?? '', this.parMois())));
  protected readonly libellesCourts = computed(() => (this.rapport()?.serie ?? []).map((p) => libelleCourt(p.cle ?? '', this.parMois())));
  private readonly parMois = computed(() => this.rapport()?.pas === 'MOIS');

  protected readonly series = computed<SerieCourbe[]>(() => {
    const r = this.rapport();
    if (!r) return [];
    return [
      { nom: 'Chiffre d’affaires', valeurs: (r.serie ?? []).map((p) => p.chiffreAffaires ?? 0), couleur: 'var(--gs-serie-1)' },
      { nom: 'Marge', valeurs: (r.serie ?? []).map((p) => p.marge ?? 0), couleur: 'var(--gs-serie-2)' },
      {
        nom: 'CA de la période précédente',
        valeurs: (r.seriePrecedente ?? []).map((p) => p.chiffreAffaires ?? 0),
        couleur: 'var(--gs-serie-reference)',
        pointilles: true,
      },
    ];
  });

  protected readonly parCategorie = computed(() => barres(this.rapport()?.parCategorie));
  protected readonly parSite = computed(() => barres(this.rapport()?.parSite));
  protected readonly parVendeur = computed(() => barres(this.rapport()?.parVendeur));
  protected readonly parHeure = computed<Colonne[]>(() =>
    (this.rapport()?.parHeure ?? []).map((p) => ({
      libelle: `${p.cle} h`,
      valeur: p.chiffreAffaires ?? 0,
      detail: `${p.tickets} ticket${(p.tickets ?? 0) > 1 ? 's' : ''}`,
    })),
  );
  protected readonly parJour = computed<Colonne[]>(() =>
    (this.rapport()?.parJourSemaine ?? []).map((p) => ({
      libelle: JOURS[Number(p.cle) - 1] ?? p.cle ?? '',
      valeur: p.chiffreAffaires ?? 0,
      detail: `${p.tickets} ticket${(p.tickets ?? 0) > 1 ? 's' : ''}`,
    })),
  );
  /** L'heure la plus forte, dite en toutes lettres : le dessin la montre, la phrase la nomme. */
  protected readonly heureForte = computed(() => plusFort(this.parHeure()));
  protected readonly jourFort = computed(() => {
    const fort = plusFort(this.parJour());
    return fort ? { ...fort, libelle: JOURS_ENTIERS[JOURS.indexOf(fort.libelle)] ?? fort.libelle } : null;
  });

  protected readonly periode = computed(() => {
    const r = this.rapport();
    return r ? `${jourLisible(r.debut)} – ${jourLisible(r.fin)}` : '';
  });

  ngOnInit(): void {
    this.charger();
  }

  protected choisirPreset(preset: Preset): void {
    this.preset.set(preset);
    if (preset === 'libre') return;
    const aujourdhui = new Date();
    const [debut, fin] = bornes(preset, aujourdhui);
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

  protected variation(v: number | null): string {
    if (v == null) return '—';
    const signe = v > 0 ? '+' : '';
    return `${signe}${v.toLocaleString('fr-FR', { maximumFractionDigits: 1 })} %`;
  }

  private charger(): void {
    if (this.debut() > this.fin()) {
      this.erreur.set('La date de début est après la date de fin.');
      return;
    }
    this.chargement.set(true);
    this.erreur.set(null);
    this.http
      .get<RapportVentesDto>(RACINE, { params: { debut: this.debut(), fin: this.fin(), tousSites: this.tousSites() } })
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

function tuile(libelle: string, valeur: number | undefined, precedent: number | undefined, annee: number | undefined,
               unite: string, detail: string | null): Tuile {
  return {
    libelle,
    valeur: valeur ?? 0,
    unite,
    detail,
    vsPrecedent: ecart(valeur, precedent),
    vsAnnee: ecart(valeur, annee),
  };
}

/** L'ecart en pourcentage ; nul quand la reference est nulle — « +∞ % » ne dit rien. */
function ecart(valeur: number | undefined, reference: number | undefined): number | null {
  if (valeur == null || !reference) return null;
  return Math.round(((valeur - reference) / Math.abs(reference)) * 1000) / 10;
}

function barres(parts: Repartition[] | undefined): Barre[] {
  return (parts ?? []).map((p) => ({
    libelle: p.libelle ?? '—',
    valeur: p.chiffreAffaires ?? 0,
    detail: [
      p.part != null ? `${p.part.toLocaleString('fr-FR')} % du chiffre` : null,
      p.tauxMarge != null ? `marge ${p.tauxMarge.toLocaleString('fr-FR')} %` : null,
      `${p.tickets} ticket${(p.tickets ?? 0) > 1 ? 's' : ''}`,
    ].filter(Boolean).join(' · '),
  }));
}

function plusFort(colonnes: Colonne[]): Colonne | null {
  const avecVentes = colonnes.filter((c) => c.valeur > 0);
  return avecVentes.length === 0 ? null : avecVentes.reduce((a, b) => (b.valeur > a.valeur ? b : a));
}

function bornes(preset: Preset, aujourdhui: Date): [Date, Date] {
  const jour = (d: Date, n: number) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
  switch (preset) {
    case 'jour':
      return [aujourdhui, aujourdhui];
    case 'semaine':
      return [jour(aujourdhui, -6), aujourdhui];
    case 'mois-dernier':
      return [new Date(aujourdhui.getFullYear(), aujourdhui.getMonth() - 1, 1), new Date(aujourdhui.getFullYear(), aujourdhui.getMonth(), 0)];
    case 'annee':
      return [new Date(aujourdhui.getFullYear(), 0, 1), aujourdhui];
    default:
      return [debutDuMois(aujourdhui), aujourdhui];
  }
}

function debutDuMois(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), 1);
}

/** AAAA-MM-JJ dans le fuseau de l'appareil : c'est le jour que le gerant a sous les yeux. */
function iso(d: Date): string {
  const p = (n: number) => `${n}`.padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

function enDate(cle: string): Date {
  const [a, m, j] = cle.split('-').map(Number);
  return new Date(a, (m ?? 1) - 1, j ?? 1);
}

function libelleLong(cle: string, parMois: boolean): string {
  const d = enDate(cle);
  return parMois
    ? d.toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' })
    : d.toLocaleDateString('fr-FR', { weekday: 'short', day: 'numeric', month: 'short' });
}

function libelleCourt(cle: string, parMois: boolean): string {
  const d = enDate(cle);
  return parMois ? d.toLocaleDateString('fr-FR', { month: 'short' }) : d.toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' });
}

function jourLisible(date: string | undefined): string {
  return date ? enDate(date).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' }) : '';
}
