import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import { Entreprise } from '../noyau/entreprise';

const API = `${environnement.api}/gestiondestock/v1/fidelite/politique`;

/** Ce que rapporte un achat, et ce que valent les points. Ecrit a la main, en attendant la regeneration. */
export interface PolitiqueFideliteDto {
  fideliteActive?: boolean;
  /** Ce qu'il faut payer, TTC et sur un seul ticket, pour gagner un point. */
  montantParPoint?: number;
  /** Ce que vaut un point, en francs, une fois echange contre un bon. */
  valeurPointFcfa?: number;
  /** Le moins de points qu'on puisse echanger d'un coup. */
  pointsMinimumBon?: number;
  /** Combien de jours un bon reste valable. */
  dureeValiditeBonJours?: number;
}

@Injectable({ providedIn: 'root' })
export class PolitiqueFidelite {
  private readonly http = inject(HttpClient);
  private readonly entreprise = inject(Entreprise);

  lire(): Observable<PolitiqueFideliteDto> {
    return this.http.get<PolitiqueFideliteDto>(API);
  }

  /**
   * Enregistre la politique, puis relit l'identite gardee : le ticket imprime les points d'apres
   * `montantParPoint`, et le prochain ticket doit porter le nouveau reglage.
   */
  enregistrer(politique: PolitiqueFideliteDto): Observable<PolitiqueFideliteDto> {
    return this.http.put<PolitiqueFideliteDto>(API, politique).pipe(
      tap(() => this.entreprise.relire().subscribe({ error: () => undefined })),
    );
  }
}
