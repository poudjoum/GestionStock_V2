import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import type { components } from '../api/schema';
import type { LigneInventaireDto, Page } from '../noyau/api';

export type EtatDuStockDto = components['schemas']['EtatDuStockDto'];
export type EtatDeCaisseDto = components['schemas']['EtatDeCaisseDto'];
export type FactureDto = components['schemas']['FactureDto'];

const API = `${environnement.api}/gestiondestock/v1`;

/**
 * Ce que le tableau de bord lit.
 *
 * Rien de nouveau cote serveur : `/stock/etat` et `/caisse/etat` existaient depuis leurs lots
 * respectifs, et personne ne les regardait. Un gerant qui se connecte veut savoir ce que vaut son
 * magasin et ce qu'a fait sa caisse aujourd'hui — pas atterrir sur un ecran de vente.
 */
@Injectable({ providedIn: 'root' })
export class Accueil {
  private readonly http = inject(HttpClient);

  etatDuStock(): Observable<EtatDuStockDto> {
    return this.http.get<EtatDuStockDto>(`${API}/stock/etat`);
  }

  /** La caisse du jour : sans dates, l'API rend la journee en cours. */
  etatDeCaisse(): Observable<EtatDeCaisseDto> {
    return this.http.get<EtatDeCaisseDto>(`${API}/caisse/etat`);
  }

  alertes(): Observable<LigneInventaireDto[]> {
    return this.http.get<LigneInventaireDto[]>(`${API}/stock/alertes`);
  }

  dernieresFactures(taille = 5): Observable<Page<FactureDto>> {
    return this.http.get<Page<FactureDto>>(`${API}/factures`, {
      params: { page: 0, size: taille, sort: 'dateEmission,desc' },
    });
  }
}
