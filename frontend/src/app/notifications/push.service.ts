import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { SwPush } from '@angular/service-worker';
import { firstValueFrom, take } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import { DELAI } from '../noyau/intercepteur-delai';
import { Notifications } from './notifications.service';

const RACINE = `${environnement.api}/gestiondestock/v1/notifications/push`;

/**
 * Ou en est cet appareil.
 *
 * `indisponible` porte sa raison : la personne doit savoir si c'est son navigateur, l'adresse par
 * laquelle elle ouvre l'application, ou le serveur — trois remedes differents.
 */
export type EtatPush =
  | { etat: 'verification' }
  | { etat: 'indisponible'; raison: string }
  | { etat: 'refusee' }
  | { etat: 'inactive' }
  | { etat: 'active' };

/**
 * Etre prevenu sur cet appareil, meme application fermee.
 *
 * Le navigateur s'abonne aupres de son service push avec la cle VAPID du serveur, et donne au
 * serveur l'adresse et les cles de cet abonnement. Le serveur y deposera, chiffrees, les alertes
 * du compte ; le service worker d'Angular les affiche, et un clic ramene a l'ecran concerne.
 *
 * <b>Un appareil, un compte.</b> Sur une caisse partagee, l'abonnement suit le dernier compte
 * connecte : c'est pourquoi il est redonne au serveur a chaque ouverture, et retire a la
 * deconnexion. Celui qui part ne doit plus y recevoir ses alertes.
 */
@Injectable({ providedIn: 'root' })
export class NotificationsPush {
  private readonly http = inject(HttpClient);
  private readonly swPush = inject(SwPush);
  private readonly notifications = inject(Notifications);

  private readonly courant = signal<EtatPush>({ etat: 'verification' });
  readonly etat = this.courant.asReadonly();

  private clePublique: string | null = null;
  private ecoute = false;

  /**
   * Fait le point au demarrage, et redonne l'abonnement au compte connecte.
   *
   * Redonner a chaque ouverture regle deux cas d'un coup : l'abonnement perdu par le serveur, et
   * la caisse partagee ou un autre compte vient de se connecter.
   */
  async demarrer(): Promise<void> {
    if (!this.swPush.isEnabled || typeof Notification === 'undefined') {
      // Le service worker n'existe qu'en HTTPS : c'est le cas le plus frequent au magasin, ou
      // l'application s'ouvre en clair sur le reseau local.
      this.courant.set({
        etat: 'indisponible',
        raison: location.protocol === 'https:'
          ? 'Ce navigateur ne sait pas recevoir de notifications.'
          : 'Il faut ouvrir l’application en HTTPS pour recevoir des notifications sur cet appareil.',
      });
      return;
    }
    this.ecouterLesMessages();

    try {
      this.clePublique ??= await this.lireLaCle();
    } catch {
      this.courant.set({ etat: 'indisponible', raison: 'Le serveur ne répond pas.' });
      return;
    }
    if (!this.clePublique) {
      this.courant.set({
        etat: 'indisponible',
        raison: 'Les notifications sur l’appareil ne sont pas activées sur ce serveur.',
      });
      return;
    }
    if (Notification.permission === 'denied') {
      this.courant.set({ etat: 'refusee' });
      return;
    }

    const abonnement = await firstValueFrom(this.swPush.subscription.pipe(take(1)));
    if (abonnement && Notification.permission === 'granted') {
      try {
        await this.enregistrer(abonnement);
        this.courant.set({ etat: 'active' });
      } catch {
        // Le serveur l'a refuse ou ne repond pas : l'appareil reste abonne chez son service push,
        // et sera redonne a la prochaine ouverture.
        this.courant.set({ etat: 'active' });
      }
    } else {
      this.courant.set({ etat: 'inactive' });
    }
  }

  /**
   * Abonne cet appareil. A appeler depuis un geste de la personne : le navigateur refuse de
   * demander la permission autrement.
   */
  async activer(): Promise<void> {
    if (!this.clePublique) {
      return;
    }
    try {
      const abonnement = await this.swPush.requestSubscription({ serverPublicKey: this.clePublique });
      await this.enregistrer(abonnement);
      this.courant.set({ etat: 'active' });
    } catch (echec: unknown) {
      if (Notification.permission === 'denied') {
        this.courant.set({ etat: 'refusee' });
        return;
      }
      throw echec;
    }
  }

  /** Desabonne cet appareil : chez le serveur, puis chez le service push. */
  async desactiver(): Promise<void> {
    const abonnement = await firstValueFrom(this.swPush.subscription.pipe(take(1)));
    if (abonnement) {
      await firstValueFrom(
        this.http.delete<void>(`${RACINE}/abonnements`, { body: { endpoint: abonnement.endpoint } }),
      ).catch(() => undefined);
      // Meme si le serveur n'a pas repondu : desabonne chez le service push, l'appareil ne
      // recevra plus rien, et le serveur l'oubliera au premier envoi, qui lui rendra 410.
      await this.swPush.unsubscribe().catch(() => undefined);
    }
    this.courant.set({ etat: 'inactive' });
  }

  /**
   * A la deconnexion : retirer cet appareil du compte qui part.
   *
   * Borne a trois secondes : se deconnecter ne doit pas attendre un serveur qui ne repond pas.
   */
  async oublierCetAppareil(): Promise<void> {
    if (!this.swPush.isEnabled || this.courant().etat !== 'active') {
      return;
    }
    await Promise.race([
      this.desactiver(),
      new Promise<void>((fin) => setTimeout(fin, 3000)),
    ]);
  }

  private async lireLaCle(): Promise<string | null> {
    const reponse = await firstValueFrom(
      this.http.get<{ clePublique: string } | null>(`${RACINE}/cle`, {
        context: new HttpContext().set(DELAI, 8_000),
      }),
    );
    return reponse?.clePublique ?? null;
  }

  private enregistrer(abonnement: PushSubscription): Promise<void> {
    return firstValueFrom(this.http.post<void>(`${RACINE}/abonnements`, abonnement.toJSON()));
  }

  /**
   * Une alerte arrivee pendant que l'application est ouverte : la cloche doit monter tout de
   * suite, sans attendre le prochain ecran.
   */
  private ecouterLesMessages(): void {
    if (this.ecoute) {
      return;
    }
    this.ecoute = true;
    this.swPush.messages.subscribe(() => this.notifications.rafraichirLeCompte());
  }
}
