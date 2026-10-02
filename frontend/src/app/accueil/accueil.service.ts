import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import type { components } from '../api/schema';
import type { LigneInventaireDto, Page } from '../noyau/api';
import type { CampagneDuJour, PromotionsDuJourDto } from '../comptoir/prix-promotionnel';

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

  /**
   * Le nombre de factures qui attendent un encaissement. Une page d'un element suffit : c'est le
   * total que donne la pagination qui compte, pas les factures elles-memes.
   */
  facturesDues(): Observable<number> {
    return this.http
      .get<Page<FactureDto>>(`${API}/factures`, { params: { statut: 'DUES', page: 0, size: 1 } })
      .pipe(map((page) => page.totalElements ?? 0));
  }

  /** Les campagnes en cours, pour les rappeler en tete de l'accueil. */
  campagnesEnCours(): Observable<CampagneDuJour[]> {
    return this.http
      .get<PromotionsDuJourDto>(`${API}/campagnes/promotions-en-cours`)
      .pipe(map((jour) => jour.campagnes ?? []));
  }
}
