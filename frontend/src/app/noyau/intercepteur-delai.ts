import {
  HttpContextToken,
  HttpErrorResponse,
  HttpInterceptorFn,
} from '@angular/common/http';
import { TimeoutError, catchError, throwError, timeout } from 'rxjs';

/**
 * Le temps au-dela duquel on cesse d'attendre le serveur.
 *
 * Vingt secondes : bien plus qu'il n'en faut a la moindre requete, et bien moins que ce qu'un
 * caissier peut passer devant un bouton gris pendant qu'un client attend sa monnaie.
 */
const DEFAUT_MS = 20_000;

/**
 * Le delai de cette requete-la, quand il doit differer.
 *
 * Un import de dix mille articles ou une construction de rapport prennent legitimement plus
 * longtemps : ils posent leur propre valeur plutot que de faire remonter le delai general, ce qui
 * rendrait le comptoir muet aussi longtemps qu'eux.
 */
export const DELAI = new HttpContextToken<number>(() => DEFAUT_MS);

/**
 * Coupe court aux requetes qui ne reviennent pas.
 *
 * Sans ce garde, une requete peut rester en vol bien plus longtemps qu'on ne l'imagine, sans
 * erreur et sans reponse : c'est ce qui arrive quand le serveur est remplace pendant un
 * deploiement. Nginx accepte la connexion, la garde le temps de son propre delai de lecture — une
 * minute — et le navigateur, lui, ne voit rien du tout. Au comptoir, le bouton reste sur
 * « Enregistrement… » et le caissier ne sait pas s'il doit recommencer. C'est arrive.
 *
 * L'expiration est convertie en erreur HTTP de statut 0, celle que le navigateur rend deja quand
 * il ne joint personne. Les ecrans n'ont ainsi rien de particulier a traiter : ils affichent le
 * meme message que pour une coupure de reseau, qui est exactement ce dont il s'agit.
 */
export const intercepteurDelai: HttpInterceptorFn = (requete, suivant) => {
  return suivant(requete).pipe(
    timeout({ each: requete.context.get(DELAI) }),
    catchError((echec: unknown) => {
      if (echec instanceof TimeoutError) {
        return throwError(
          () =>
            new HttpErrorResponse({
              status: 0,
              statusText: 'Delai depasse',
              url: requete.url,
            }),
        );
      }
      return throwError(() => echec);
    }),
  );
};
