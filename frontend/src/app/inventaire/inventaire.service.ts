import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import type { Page } from '../noyau/api';

const API = `${environnement.api}/gestiondestock/v1`;

/**
 * Les formes de l'inventaire.
 *
 * Ecrites ici a la main, et c'est provisoire : `npm run api:types` lit la specification du serveur
 * deploye, qui ne connait pas encore ces routes. Elles descendront d'elles-memes dans
 * `api/schema.d.ts` a la prochaine regeneration, et ce bloc pourra tomber.
 */
export type StatutSeance = 'OUVERTE' | 'VALIDEE' | 'ABANDONNEE';

export interface SeanceInventaireDto {
  id?: number;
  reference?: string;
  dateOuverture?: string;
  dateCloture?: string;
  statut?: StatutSeance;
  commentaire?: string;
  idEntreprise?: number;
  articles?: number;
  comptes?: number;
  ecarts?: number;
}

export interface LigneComptageDto {
  id?: number;
  idArticle?: number;
  codeArticle?: string;
  designation?: string;
  quantiteTheorique?: number;
  quantiteComptee?: number;
  /** Comptee moins theorique fige : ce qu'on compare. */
  ecart?: number;
  /** Ce que la validation posera reellement en mouvement de stock. */
  correction?: number;
  compteLe?: string;
}

/** Les trois vues de la liste. Elles ne se recouvrent pas. */
export type VueDesLignes = 'TOUTES' | 'A_COMPTER' | 'ECARTS';

@Injectable({ providedIn: 'root' })
export class Inventaire {
  private readonly http = inject(HttpClient);

  /**
   * La seance en cours.
   *
   * Le serveur rend 204 quand il n'y en a pas — ne pas avoir d'inventaire ouvert est l'etat
   * ordinaire d'un magasin, pas une erreur. Angular rend alors un corps nul.
   */
  ouverte(): Observable<SeanceInventaireDto | null> {
    return this.http.get<SeanceInventaireDto | null>(`${API}/inventaires/ouverte`);
  }

  ouvrir(commentaire: string): Observable<SeanceInventaireDto> {
    return this.http.post<SeanceInventaireDto>(`${API}/inventaires`, { commentaire });
  }

  seance(id: number): Observable<SeanceInventaireDto> {
    return this.http.get<SeanceInventaireDto>(`${API}/inventaires/${id}`);
  }

  lignes(
    idSeance: number,
    q: string,
    vue: VueDesLignes,
    page = 0,
    taille = 50,
  ): Observable<Page<LigneComptageDto>> {
    return this.http.get<Page<LigneComptageDto>>(`${API}/inventaires/${idSeance}/lignes`, {
      params: { q, vue, page, size: taille, sort: 'designation,asc' },
    });
  }

  /** Par code-barres quand la douchette a lu, par identifiant quand on a clique dans la liste. */
  compter(
    idSeance: number,
    cible: { idArticle?: number; codeArticle?: string },
    quantite: number,
  ): Observable<LigneComptageDto> {
    return this.http.post<LigneComptageDto>(`${API}/inventaires/${idSeance}/comptages`, {
      ...cible,
      quantite,
    });
  }

  annulerComptage(idSeance: number, idLigne: number): Observable<LigneComptageDto> {
    return this.http.delete<LigneComptageDto>(
      `${API}/inventaires/${idSeance}/comptages/${idLigne}`,
    );
  }

  valider(idSeance: number): Observable<SeanceInventaireDto> {
    return this.http.post<SeanceInventaireDto>(`${API}/inventaires/${idSeance}/validation`, {});
  }

  abandonner(idSeance: number): Observable<SeanceInventaireDto> {
    return this.http.post<SeanceInventaireDto>(`${API}/inventaires/${idSeance}/abandon`, {});
  }

  historique(page = 0, taille = 10): Observable<Page<SeanceInventaireDto>> {
    return this.http.get<Page<SeanceInventaireDto>>(`${API}/inventaires`, {
      params: { page, size: taille },
    });
  }
}
