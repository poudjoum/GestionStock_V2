import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import type { components } from '../api/schema';

type Schemas = components['schemas'];
export type TransfertDto = Schemas['TransfertDto'];
export type LigneTransfertDto = Schemas['LigneTransfertDto'];
export type ReceptionTransfertDto = Schemas['ReceptionTransfertDto'];
export type EtatTransfert = NonNullable<TransfertDto['etat']>;

const API = `${environnement.api}/gestiondestock/v1/transferts`;

@Injectable({ providedIn: 'root' })
export class ServiceTransferts {
  private readonly http = inject(HttpClient);

  lister(etat: EtatTransfert | null = null): Observable<TransfertDto[]> {
    return this.http.get<TransfertDto[]>(API, { params: etat ? { etat } : {} });
  }

  creer(transfert: TransfertDto): Observable<TransfertDto> {
    return this.http.post<TransfertDto>(API, transfert);
  }

  ajouterLigne(id: number, ligne: LigneTransfertDto): Observable<TransfertDto> {
    return this.http.post<TransfertDto>(`${API}/${id}/lignes`, ligne);
  }

  retirerLigne(id: number, idLigne: number): Observable<TransfertDto> {
    return this.http.delete<TransfertDto>(`${API}/${id}/lignes/${idLigne}`);
  }

  expedier(id: number): Observable<TransfertDto> {
    return this.http.post<TransfertDto>(`${API}/${id}/expedition`, null);
  }

  recevoir(id: number, receptions: ReceptionTransfertDto[]): Observable<TransfertDto> {
    return this.http.post<TransfertDto>(`${API}/${id}/reception`, receptions);
  }

  annuler(id: number): Observable<TransfertDto> {
    return this.http.post<TransfertDto>(`${API}/${id}/annulation`, null);
  }
}
