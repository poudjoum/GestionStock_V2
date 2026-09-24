import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import type { components } from '../api/schema';
import type { ReglementDto } from '../noyau/reglements';
import type { Page } from '../noyau/api';

export type FactureDto = components['schemas']['FactureDto'];

// Les moyens de paiement et leurs libelles vivent dans le noyau : le comptoir les lit aussi, et
// le ticket les imprime.
export type { ModeReglement, ReglementDto } from '../noyau/reglements';
export { MODES_DE_REGLEMENT, libelleDuMode } from '../noyau/reglements';

const API = `${environnement.api}/gestiondestock/v1`;

@Injectable({ providedIn: 'root' })
export class Factures {
  private readonly http = inject(HttpClient);

  /** `statut` vaut IMPAYEE, PARTIELLEMENT_REGLEE, REGLEE, ANNULEE ou DUES. */
  lister(q: string, statut: string, page = 0, taille = 25): Observable<Page<FactureDto>> {
    return this.http.get<Page<FactureDto>>(`${API}/factures`, {
      params: { q, statut, page, size: taille, sort: 'dateEmission,desc' },
    });
  }

  /** Le detail porte les lignes ; la liste ne rend que les en-tetes. */
  detail(id: number): Observable<FactureDto> {
    return this.http.get<FactureDto>(`${API}/factures/${id}`);
  }

  reglements(id: number): Observable<ReglementDto[]> {
    return this.http.get<ReglementDto[]>(`${API}/factures/${id}/reglements`);
  }

  regler(id: number, reglement: ReglementDto): Observable<ReglementDto> {
    return this.http.post<ReglementDto>(`${API}/factures/${id}/reglements`, reglement);
  }

  /** Reprendre un encaissement touche a une recette deja constatee : ADMIN et COMPTABLE seuls. */
  supprimerReglement(idFacture: number, idReglement: number): Observable<void> {
    return this.http.delete<void>(`${API}/factures/${idFacture}/reglements/${idReglement}`);
  }

  annuler(id: number): Observable<FactureDto> {
    return this.http.post<FactureDto>(`${API}/factures/${id}/annulation`, {});
  }
}
