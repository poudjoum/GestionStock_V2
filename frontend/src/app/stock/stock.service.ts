import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import type { LigneInventaireDto, Page } from '../noyau/api';
import type { components } from '../api/schema';

export type MvtStkDto = components['schemas']['MvtStkDto'];
export type MotifMvtStk = NonNullable<MvtStkDto['motif']>;

const RACINE = `${environnement.api}/gestiondestock/v1/stock`;
const MOUVEMENTS = `${environnement.api}/gestiondestock/v1/mouvements`;

@Injectable({ providedIn: 'root' })
export class Stock {
  private readonly http = inject(HttpClient);

  /** L'inventaire, filtre par `q` sur le code et la designation. */
  inventaire(q: string, page = 0, taille = 25): Observable<Page<LigneInventaireDto>> {
    return this.http.get<Page<LigneInventaireDto>>(`${RACINE}/inventaire`, {
      params: { q, page, size: taille, sort: 'designation,asc' },
    });
  }

  /** Ce qu'il faut recommander, et ce qu'il faut compter. */
  alertes(): Observable<LigneInventaireDto[]> {
    return this.http.get<LigneInventaireDto[]>(`${RACINE}/alertes`);
  }

  /**
   * Un ajustement saisi a la main : une casse, une peremption, un retour. Le serveur refuse les
   * motifs qui appartiennent a un document — une vente, une livraison.
   */
  ajuster(sens: 'entree' | 'sortie', mouvement: MvtStkDto): Observable<MvtStkDto> {
    return this.http.post<MvtStkDto>(`${MOUVEMENTS}/${sens}`, mouvement);
  }
}
