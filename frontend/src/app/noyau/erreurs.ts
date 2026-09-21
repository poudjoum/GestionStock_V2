import { HttpErrorResponse } from '@angular/common/http';
import type { ErreurApi } from './api';

/**
 * Le message a montrer quand une requete echoue.
 *
 * L'API rend des messages ecrits pour etre lus — « La quantite recue depasse ce qui reste
 * attendu : 4 attendus, 6 recus ». Les remplacer par un « une erreur est survenue » generique
 * effacerait la seule information utile a celui qui est devant l'ecran.
 */
export function messageDErreur(echec: unknown, defaut: string): string {
  if (!(echec instanceof HttpErrorResponse)) {
    return defaut;
  }
  if (echec.status === 0) {
    return 'Le serveur ne répond pas. Vérifiez votre connexion.';
  }
  const corps = echec.error as ErreurApi | undefined;
  if (corps?.message) {
    // Les precisions accompagnent le message : elles disent quelle ligne pose probleme.
    const details = corps.errors?.length ? ` (${corps.errors.join(' ; ')})` : '';
    return corps.message + details;
  }
  return defaut;
}
