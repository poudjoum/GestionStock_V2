import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, catchError, filter, switchMap, take, throwError } from 'rxjs';
import { Session } from './session';

/** Les routes qui ne portent pas de jeton, et qui ne doivent pas en declencher le renouvellement. */
const ROUTES_LIBRES = ['/api/auth/signin', '/api/auth/refresh', '/api/auth/logout'];

/**
 * Un seul rafraichissement a la fois, partage par toutes les requetes qui attendent.
 *
 * Au demarrage, l'application lance volontiers cinq requetes d'un coup. Si le jeton a expire
 * pendant la nuit, les cinq recoivent un 401 en meme temps : sans ce verrou, elles demanderaient
 * cinq rafraichissements. Le premier reussirait, les quatre autres presenteraient un jeton deja
 * remplace — que le serveur tient pour une copie volee, et qui ferme tout le compte.
 */
let renouvellementEnCours = false;
const jetonRenouvele = new BehaviorSubject<string | null>(null);

export const intercepteurJeton: HttpInterceptorFn = (requete, suivant) => {
  const session = inject(Session);
  const router = inject(Router);

  if (ROUTES_LIBRES.some((route) => requete.url.includes(route))) {
    return suivant(requete);
  }

  const jeton = session.jeton();
  const signee = jeton ? porteur(requete, jeton) : requete;

  return suivant(signee).pipe(
    catchError((erreur: unknown) => {
      const estUn401 = erreur instanceof HttpErrorResponse && erreur.status === 401;
      if (!estUn401 || !session.jetonDeRafraichissement()) {
        return throwError(() => erreur);
      }
      return renouvelerPuisRejouer(requete, suivant, session, router);
    }),
  );
};

function renouvelerPuisRejouer(
  requete: HttpRequest<unknown>,
  suivant: HttpHandlerFn,
  session: Session,
  router: Router,
): Observable<HttpEvent<unknown>> {
  if (renouvellementEnCours) {
    // Attendre celui qui est parti, puis rejouer avec le jeton qu'il rapporte.
    return jetonRenouvele.pipe(
      filter((jeton): jeton is string => jeton !== null),
      take(1),
      switchMap((jeton) => suivant(porteur(requete, jeton))),
    );
  }

  renouvellementEnCours = true;
  jetonRenouvele.next(null);

  return session.rafraichissement$().pipe(
    switchMap((jeton) => {
      renouvellementEnCours = false;
      jetonRenouvele.next(jeton);
      return suivant(porteur(requete, jeton));
    }),
    catchError((echec: unknown) => {
      // Le jeton de rafraichissement ne vaut plus : expire, revoque, ou le compte a ete ferme.
      // Il n'y a rien a retenter — on repart de la page de connexion.
      renouvellementEnCours = false;
      session.oublier();
      void router.navigate(['/connexion']);
      return throwError(() => echec);
    }),
  );
}

function porteur(requete: HttpRequest<unknown>, jeton: string): HttpRequest<unknown> {
  return requete.clone({ setHeaders: { Authorization: `Bearer ${jeton}` } });
}
