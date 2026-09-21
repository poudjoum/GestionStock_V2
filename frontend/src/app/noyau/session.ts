import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map, tap } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import type { JwtResponse, UserDto } from './api';
import type { Role } from './roles';

/** Ce qu'on garde d'une connexion entre deux ouvertures de l'application. */
interface SessionStockee {
  jeton: string;
  rafraichissement: string;
  username: string;
  roles: Role[];
}

const CLE = 'gestionstock.session';

/**
 * Qui est connecte, et de quoi parler a l'API en son nom.
 *
 * Les deux jetons sont conserves dans le stockage local : sans cela, recharger la page
 * deconnecterait, ce qui est intenable sur un telephone ou l'application est fermee et rouverte
 * cent fois par jour.
 *
 * Les roles y sont conserves aussi, mais uniquement pour dessiner le menu sans attendre un
 * aller-retour. Ils ne font pas autorite : `chargerLeCompte` les redemande au serveur a chaque
 * demarrage, et c'est l'API qui refuse ce qu'elle doit refuser. Un role retire ne survit donc
 * pas au prochain chargement.
 */
@Injectable({ providedIn: 'root' })
export class Session {
  private readonly http = inject(HttpClient);

  private readonly etat = signal<SessionStockee | null>(lire());
  private readonly compte = signal<UserDto | null>(null);

  readonly connecte = computed(() => this.etat() !== null);
  readonly jeton = computed(() => this.etat()?.jeton ?? null);
  readonly jetonDeRafraichissement = computed(() => this.etat()?.rafraichissement ?? null);
  readonly roles = computed<Role[]>(() => this.etat()?.roles ?? []);
  readonly username = computed(() => this.etat()?.username ?? null);
  readonly moi = this.compte.asReadonly();

  seConnecter(username: string, motDePasse: string): Observable<JwtResponse> {
    return this.http
      .post<JwtResponse>(`${environnement.api}/api/auth/signin`, { username, password: motDePasse })
      .pipe(tap((reponse) => this.retenir(reponse)));
  }

  /**
   * Redemande au serveur qui porte ce jeton.
   *
   * C'est ce qui empeche le stockage local de faire autorite : un role retire pendant la nuit
   * disparait du menu au premier chargement du matin, sans attendre l'expiration du jeton.
   */
  chargerLeCompte(): Observable<UserDto> {
    return this.http
      .get<UserDto>(`${environnement.api}/gestiondestock/v1/users/moi`)
      .pipe(
        tap((compte) => {
          this.compte.set(compte);
          const roles = (compte.roles ?? [])
            .map((role) => role.roleName)
            .filter((nom): nom is Role => !!nom);
          const courant = this.etat();
          if (courant) {
            this.ecrire({ ...courant, roles, username: compte.username ?? courant.username });
          }
        }),
      );
  }

  /**
   * Echange le jeton de rafraichissement contre un couple neuf, et rend le jeton d'acces.
   *
   * Appele par l'intercepteur, qui en a besoin pour rejouer la requete qui a echoue.
   */
  rafraichissement$(): Observable<string> {
    const refreshToken = this.jetonDeRafraichissement();
    return this.http
      .post<JwtResponse>(`${environnement.api}/api/auth/refresh`, { refreshToken })
      .pipe(
        tap((reponse) => this.retenir(reponse)),
        map((reponse) => reponse.accessToken ?? ''),
      );
  }

  /**
   * Se deconnecte de cet appareil seulement.
   *
   * L'appel au serveur revoque le jeton ; on oublie la session localement quoi qu'il arrive, y
   * compris si l'appel echoue. Rester connecte parce que le reseau est tombe au moment ou l'on
   * demandait a partir serait le contraire de ce qui est demande.
   */
  seDeconnecter(): void {
    const refreshToken = this.jetonDeRafraichissement();
    if (refreshToken) {
      this.http
        .post(`${environnement.api}/api/auth/logout`, { refreshToken })
        .subscribe({ error: () => undefined });
    }
    this.oublier();
  }

  oublier(): void {
    this.etat.set(null);
    this.compte.set(null);
    essayer(() => localStorage.removeItem(CLE));
  }

  private retenir(reponse: JwtResponse): void {
    this.ecrire({
      jeton: reponse.accessToken ?? '',
      rafraichissement: reponse.refreshToken ?? '',
      username: reponse.username ?? '',
      roles: (reponse.roles ?? []) as Role[],
    });
  }

  private ecrire(session: SessionStockee): void {
    this.etat.set(session);
    essayer(() => localStorage.setItem(CLE, JSON.stringify(session)));
  }
}

function lire(): SessionStockee | null {
  try {
    const brut = localStorage.getItem(CLE);
    return brut ? (JSON.parse(brut) as SessionStockee) : null;
  } catch {
    // Navigation privee, stockage refuse, donnees abimees : on repart d'une session vide plutot
    // que d'empecher l'application de demarrer.
    return null;
  }
}

function essayer(action: () => void): void {
  try {
    action();
  } catch {
    // Idem : ne pas pouvoir ecrire dans le stockage local n'est pas une raison de tomber.
  }
}
