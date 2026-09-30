import { HttpClient, HttpContext, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { catchError, of, tap } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import { DELAI } from './intercepteur-delai';
import { Session } from './session';

/** Tous les combien on revient frapper a la porte d'un serveur qui ne repondait plus. */
const SONDE_MS = 15_000;

/**
 * Le serveur repond-il ?
 *
 * `navigator.onLine` ne suffit pas : il dit si l'appareil a une connexion, pas si le serveur est
 * au bout. Derriere une liaison satellite qui decroche, ou un tunnel coupe, le telephone se croit
 * en ligne et chaque requete attend son delai complet — vingt secondes par article scanne.
 *
 * L'etat vient donc de ce que les requetes rencontrent reellement : un echec de reseau (statut 0,
 * delai depasse compris) fait passer hors ligne, n'importe quelle reponse du serveur — meme une
 * erreur — fait repasser en ligne. Hors ligne, une sonde legere repart toutes les quinze secondes
 * pour s'apercevoir du retour, meme quand personne ne vend.
 */
@Injectable({ providedIn: 'root' })
export class Reseau {
  private readonly http = inject(HttpClient);
  private readonly session = inject(Session);

  private readonly etat = signal(typeof navigator === 'undefined' ? true : navigator.onLine);
  /** Vrai tant que le serveur a repondu a la derniere requete. */
  readonly joignable = this.etat.asReadonly();

  private sonde?: ReturnType<typeof setInterval>;

  constructor() {
    if (typeof window !== 'undefined') {
      // Le navigateur sait au moins quand la connexion tombe tout a fait : inutile d'attendre
      // qu'une requete echoue pour le dire.
      window.addEventListener('offline', () => this.constater(false));
      // Son retour, en revanche, ne prouve pas que le serveur est joignable : on va voir.
      window.addEventListener('online', () => this.sonder());
    }
    if (!this.etat()) {
      this.demarrerLaSonde();
    }
  }

  /** Appele par l'intercepteur : ce qu'une requete vient d'apprendre du reseau. */
  constater(joignable: boolean): void {
    if (this.etat() === joignable) {
      return;
    }
    this.etat.set(joignable);
    if (joignable) {
      clearInterval(this.sonde);
      this.sonde = undefined;
    } else {
      this.demarrerLaSonde();
    }
  }

  /** Va voir tout de suite si le serveur repond. */
  sonder(): void {
    if (!this.session.connecte()) {
      return;
    }
    // Une route legere et authentifiee : sa reponse, quelle qu'elle soit, suffit a trancher.
    this.http
      .get(`${environnement.api}/gestiondestock/v1/users/moi`, {
        context: new HttpContext().set(DELAI, 5_000),
      })
      .pipe(catchError(() => of(null)))
      .subscribe();
  }

  private demarrerLaSonde(): void {
    this.sonde ??= setInterval(() => this.sonder(), SONDE_MS);
  }
}

/**
 * Tient `Reseau` informe de ce que rencontre chaque requete.
 *
 * Place avant l'intercepteur de delai : il voit donc une requete expiree comme ce qu'elle est
 * devenue, un echec de statut 0.
 */
export const intercepteurReseau: HttpInterceptorFn = (requete, suivant) => {
  const reseau = inject(Reseau);
  return suivant(requete).pipe(
    tap({
      next: () => reseau.constater(true),
      error: (echec: unknown) => {
        if (echec instanceof HttpErrorResponse) {
          // Toute autre reponse vient de l'application : elle est la, meme si elle refuse.
          reseau.constater(!estUneCoupure(echec));
        }
      },
    }),
  );
};

/**
 * Vrai quand l'echec dit que l'application n'est pas au bout — et non qu'elle refuse.
 *
 * Le statut 0 n'en est qu'un cas : l'appareil n'a joint personne. Mais le plus souvent, quelqu'un
 * repond a la place de l'application. Nginx rend 502 pendant que son conteneur redemarre, a
 * chaque deploiement ; Cloudflare rend 502 ou 530 quand le serveur du magasin ne repond plus au
 * tunnel. Tenir ces reponses pour la preuve que le serveur est la faisait l'inverse de ce qu'il
 * fallait : le comptoir cherchait l'article en ligne, recevait 502, et annoncait au caissier
 * qu'aucun article ne portait ce code.
 */
export function estUneCoupure(echec: unknown): boolean {
  if (!(echec instanceof HttpErrorResponse)) {
    return false;
  }
  const s = echec.status;
  // 502, 503, 504 : la passerelle n'a pas joint l'application. 520 a 530 : les memes, dits par
  // Cloudflare.
  return s === 0 || s === 502 || s === 503 || s === 504 || (s >= 520 && s <= 530);
}
