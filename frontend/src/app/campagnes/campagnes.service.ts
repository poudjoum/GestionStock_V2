import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import type { ArticleDto, Page } from '../noyau/api';
import type { StatutCampagne } from '../noyau/statuts';
import type { PromotionArticleDto } from '../comptoir/prix-promotionnel';

const API = `${environnement.api}/gestiondestock/v1`;

/** Une campagne de promotion. Ecrite a la main, en attendant la regeneration des types. */
export interface CampagneDto {
  id?: number;
  titre: string;
  message?: string | null;
  image?: string | null;
  /** « 2026-10-01 », a l'heure du magasin, bornes comprises. */
  dateDebut: string;
  dateFin: string;
  statut?: StatutCampagne;
  promotions: Partial<PromotionArticleDto>[];
}

@Injectable({ providedIn: 'root' })
export class Campagnes {
  private readonly http = inject(HttpClient);

  lister(): Observable<CampagneDto[]> {
    return this.http.get<CampagneDto[]>(`${API}/campagnes`);
  }

  /** Cree ou corrige : le serveur ne lit, d'une promotion, que l'article, le type et la valeur. */
  enregistrer(campagne: CampagneDto): Observable<CampagneDto> {
    const corps: CampagneDto = {
      ...campagne,
      promotions: campagne.promotions.map((p) => ({
        idArticle: p.idArticle,
        typeRemise: p.typeRemise,
        valeur: p.valeur,
      })),
    };
    return campagne.id
      ? this.http.put<CampagneDto>(`${API}/campagnes/${campagne.id}`, corps)
      : this.http.post<CampagneDto>(`${API}/campagnes`, corps);
  }

  arreter(id: number): Observable<CampagneDto> {
    return this.http.post<CampagneDto>(`${API}/campagnes/${id}/arret`, {});
  }

  /** Les articles du magasin dont la designation ou le code contient ce qui est tape. */
  chercherArticles(q: string): Observable<ArticleDto[]> {
    return this.http
      .get<Page<ArticleDto>>(`${API}/articles`, {
        params: { q, page: 0, size: 8, sort: 'designation,asc' },
      })
      .pipe(map((page) => page.content ?? []));
  }
}
