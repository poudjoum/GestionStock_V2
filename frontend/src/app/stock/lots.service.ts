import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import type { components } from '../api/schema';
import type { StatutAffiche } from '../noyau/statuts';

export type LotDto = components['schemas']['LotDto'];
export type RappelLotDto = components['schemas']['RappelLotDto'];
export type EtatLot = 'PERIME' | 'BIENTOT' | 'BON';

const RACINE = `${environnement.api}/gestiondestock/v1/lots`;

@Injectable({ providedIn: 'root' })
export class Lots {
  private readonly http = inject(HttpClient);

  /** Les lots en stock d'un article, premier perime en tete ; `quantite` est celle du site actif. */
  deLArticle(idArticle: number): Observable<LotDto[]> {
    return this.http.get<LotDto[]>(`${RACINE}/article/${idArticle}`);
  }

  /** Ce qui perime ou a perime et reste en rayon : une ligne par lot et par site. */
  peremption(tousSites = false): Observable<LotDto[]> {
    return this.http.get<LotDto[]>(`${RACINE}/peremption`, { params: { tousSites } });
  }

  /** Ou il reste du lot, et a qui il a ete vendu. Gerant et administrateur seulement. */
  rappel(idLot: number): Observable<RappelLotDto> {
    return this.http.get<RappelLotDto>(`${RACINE}/${idLot}/rappel`);
  }
}

/**
 * L'etat d'un lot, dit comme les autres statuts. Une DLC depassee bloque la vente : danger. Une
 * DLUO depassee se vend encore : alerte, comme ce qui approche.
 */
export function statutDuLot(lot: LotDto): StatutAffiche {
  const jours = lot.joursRestants ?? null;
  switch (lot.etat as EtatLot) {
    case 'PERIME':
      return lot.typeDate === 'DLC'
        ? { libelle: 'DLC dépassée', ton: 'danger', icone: 'block' }
        : { libelle: 'DLUO dépassée', ton: 'alerte', icone: 'history' };
    case 'BIENTOT':
      return {
        libelle: jours === 0 ? 'Aujourd’hui' : jours === 1 ? 'Demain' : `Dans ${jours} j`,
        ton: 'alerte',
        icone: 'schedule',
      };
    default:
      return { libelle: 'Bon', ton: 'ok', icone: 'check' };
  }
}
