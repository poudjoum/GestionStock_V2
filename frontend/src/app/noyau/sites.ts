import { HttpClient, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import type { components } from '../api/schema';
import { Session } from './session';

type Schemas = components['schemas'];
export type SiteDto = Schemas['SiteDto'];
export type MesSitesDto = Schemas['MesSitesDto'];
export type TypeSite = NonNullable<SiteDto['type']>;

const API = `${environnement.api}/gestiondestock/v1`;

/** L'en-tete que le serveur lit pour savoir sur quel site on travaille. */
export const ENTETE_SITE = 'X-Site';

/**
 * Le site sur lequel on travaille : celui du selecteur de l'en-tete.
 *
 * Il part avec chaque requete, et le serveur le verifie — un caissier qui ecrirait le numero d'un
 * autre magasin serait refuse. Le choix est garde sur l'appareil, par compte : le gerant qui passe
 * la matinee a l'entrepot y revient en rouvrant l'application.
 */
@Injectable({ providedIn: 'root' })
export class Sites {
  private readonly http = inject(HttpClient);
  private readonly session = inject(Session);

  private readonly miens = signal<MesSitesDto | null>(null);
  private readonly choisi = signal<number | null>(null);

  /** Les sites ou le compte travaille, actifs seulement. */
  readonly sites = computed(() => this.miens()?.sites ?? []);
  /** Le site actif : le choix gardé s'il est encore permis, sinon celui que propose le serveur. */
  readonly actif = computed<SiteDto | null>(() => {
    const sites = this.sites();
    const id = this.choisi() ?? this.miens()?.actif ?? null;
    return sites.find((s) => s.id === id) ?? sites[0] ?? null;
  });
  /** Plusieurs sites : le selecteur a lieu d'etre. */
  readonly plusieurs = computed(() => this.sites().length > 1);
  /** Peut-il regarder l'entreprise entiere — l'administrateur, le gerant, le comptable. */
  readonly tousLesSites = computed(() => this.miens()?.tousLesSites ?? false);
  /** Le site actif vend-il ? Un entrepot ne vend pas au comptoir. */
  readonly vend = computed(() => this.actif()?.type !== 'ENTREPOT');

  constructor() {
    // Un autre compte sur le meme appareil ne reprend pas le site du precedent.
    effect(() => {
      const compte = this.session.username();
      this.choisi.set(compte ? lireChoix(compte) : null);
      this.miens.set(null);
    });
  }

  /** Charge les sites du compte connecte. */
  charger(): Observable<MesSitesDto> {
    return this.http.get<MesSitesDto>(`${API}/sites/miens`).pipe(tap((m) => this.miens.set(m)));
  }

  choisir(idSite: number): void {
    this.choisi.set(idSite);
    const compte = this.session.username();
    if (compte) {
      try {
        localStorage.setItem(cle(compte), String(idSite));
      } catch {
        // Sans stockage, le choix vaut le temps de la session.
      }
    }
  }

  /** L'identifiant a envoyer au serveur, ou nul tant que les sites ne sont pas charges. */
  idActif(): number | null {
    return this.actif()?.id ?? this.choisi();
  }

  // --- La gestion des sites (reglages) ----------------------------------------------------

  tous(): Observable<SiteDto[]> {
    return this.http.get<SiteDto[]>(`${API}/sites`);
  }

  creer(site: SiteDto): Observable<SiteDto> {
    return this.http.post<SiteDto>(`${API}/sites`, site);
  }

  modifier(site: SiteDto): Observable<SiteDto> {
    return this.http.put<SiteDto>(`${API}/sites/${site.id}`, site);
  }

  fermer(id: number): Observable<SiteDto> {
    return this.http.post<SiteDto>(`${API}/sites/${id}/fermeture`, null);
  }
}

function cle(compte: string): string {
  return `gestionstock.site.${compte}`;
}

function lireChoix(compte: string): number | null {
  try {
    const valeur = localStorage.getItem(cle(compte));
    return valeur ? Number(valeur) : null;
  } catch {
    return null;
  }
}

/**
 * Pose le site actif sur chaque requete vers l'API. Pas sur `/sites/miens` : c'est elle qui dit
 * quels sites sont permis, elle ne doit pas etre refusee pour un choix devenu caduc.
 */
export const intercepteurSite: HttpInterceptorFn = (requete, suivant) => {
  if (!requete.url.includes('/gestiondestock/') || requete.url.endsWith('/sites/miens')) {
    return suivant(requete);
  }
  const id = inject(Sites).idActif();
  return suivant(id == null ? requete : requete.clone({ setHeaders: { [ENTETE_SITE]: String(id) } }));
};
