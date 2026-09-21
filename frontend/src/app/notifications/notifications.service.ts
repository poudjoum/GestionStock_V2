import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import type { NotificationDto, Page } from '../noyau/api';

const RACINE = `${environnement.api}/gestiondestock/v1/notifications`;

/**
 * Ce que le compte connecte a recu.
 *
 * Le compte des non-lues vit dans un signal : la cloche de la barre du haut et la liste le
 * partagent, et marquer une notification lue doit faire baisser le nombre sans recharger la page.
 *
 * Il se redemande a la main, et non par un minuteur. Interroger le serveur toutes les dix
 * secondes pour un compteur couterait du reseau a un telephone qui en manque, pour une
 * information qui peut attendre le prochain ecran. Le canal Web Push prendra ce relais quand il
 * existera.
 */
@Injectable({ providedIn: 'root' })
export class Notifications {
  private readonly http = inject(HttpClient);
  private readonly compte = signal(0);

  readonly nonLues = this.compte.asReadonly();

  lister(nonLuesSeulement: boolean, page = 0, taille = 20): Observable<Page<NotificationDto>> {
    return this.http.get<Page<NotificationDto>>(RACINE, {
      params: { nonLues: nonLuesSeulement, page, size: taille },
    });
  }

  rafraichirLeCompte(): void {
    this.http.get<number>(`${RACINE}/non-lues`).subscribe({
      next: (nombre) => this.compte.set(nombre),
      error: () => undefined,
    });
  }

  marquerLue(id: number): Observable<NotificationDto> {
    return this.http
      .patch<NotificationDto>(`${RACINE}/${id}/lue`, {})
      .pipe(tap(() => this.compte.update((n) => Math.max(0, n - 1))));
  }

  marquerToutesLues(): Observable<number> {
    return this.http
      .patch<number>(`${RACINE}/lues`, {})
      .pipe(tap(() => this.compte.set(0)));
  }
}
