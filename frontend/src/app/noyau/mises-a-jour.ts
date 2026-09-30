import { Injectable, inject } from '@angular/core';
import { SwUpdate } from '@angular/service-worker';
import { MatSnackBar } from '@angular/material/snack-bar';
import { filter } from 'rxjs';

/** Tous les combien on demande au serveur s'il y a une nouvelle version. */
const INTERVALLE_MS = 15 * 60_000;

/**
 * Juste apres l'ouverture, rien n'est encore en cours : passer a la nouvelle version ne coute
 * rien, et c'est le cas ordinaire — on ouvre l'application apres un deploiement.
 */
const DEMARRAGE_MS = 20_000;

/**
 * Faire passer l'application a la version deployee.
 *
 * Le service worker garde une copie de l'application pour qu'elle s'ouvre sans reseau. Au
 * deploiement, il telecharge la nouvelle en arriere-plan, mais continue de servir l'ancienne
 * jusqu'au rechargement suivant. Une caisse ouverte toute la journee restait donc sur l'ancienne
 * version, et les tickets sortaient sans le QR deploye le matin meme : c'est arrive.
 *
 * Deux cas. A l'ouverture, la bascule est immediate et silencieuse : rien n'est en cours. Plus
 * tard, elle est proposee et jamais imposee — recharger d'office effacerait le panier d'un client
 * en train de payer.
 */
@Injectable({ providedIn: 'root' })
export class MisesAJour {
  private readonly sw = inject(SwUpdate);
  private readonly snack = inject(MatSnackBar);
  private readonly ouverture = Date.now();
  private proposee = false;

  demarrer(): void {
    if (!this.sw.isEnabled) {
      // En clair sur le reseau du magasin, pas de service worker : chaque chargement lit la
      // version du serveur, il n'y a rien a faire.
      return;
    }

    this.sw.versionUpdates
      .pipe(filter((evenement) => evenement.type === 'VERSION_READY'))
      .subscribe(() => this.nouvelleVersionPrete());

    // L'etat irrecuperable : la copie gardee ne correspond plus a rien sur le serveur. Seul un
    // rechargement en sort.
    this.sw.unrecoverable.subscribe(() => location.reload());

    const verifier = () => void this.sw.checkForUpdate().catch(() => undefined);
    verifier();
    setInterval(verifier, INTERVALLE_MS);
    // Un telephone rallume ou un onglet revenu au premier plan : le moment ou l'on reprend le
    // travail, et ou il vaut la peine de verifier.
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') {
        verifier();
      }
    });
  }

  private nouvelleVersionPrete(): void {
    if (Date.now() - this.ouverture < DEMARRAGE_MS) {
      location.reload();
      return;
    }
    if (this.proposee) {
      return;
    }
    this.proposee = true;
    this.snack
      .open('Une nouvelle version de l’application est prête.', 'Mettre à jour')
      .onAction()
      .subscribe(() => location.reload());
  }
}
