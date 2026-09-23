import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import type { ArticleDto, Page } from '../noyau/api';
import type { components } from '../api/schema';

export type CategoryDto = components['schemas']['CategoryDto'];
export type { ArticleDto };

const API = `${environnement.api}/gestiondestock/v1`;

/**
 * Le catalogue : les articles et les categories qui les rangent.
 *
 * `save` cree ou modifie selon que l'identifiant est present — c'est le contrat de l'API, et il
 * evite deux methodes qui feraient la meme chose.
 */
/** Une ligne que l'import laisse de cote, avec de quoi la retrouver dans le tableur. */
export interface LigneRefusee {
  ligne: number;
  code: string | null;
  raison: string;
}

/** Ce que l'import a fait, ou ferait. Le meme rapport sert a la simulation et a l'ecriture. */
export interface RapportImport {
  simulation: boolean;
  lues: number;
  creees: number;
  modifiees: number;
  refusees: LigneRefusee[];
}

@Injectable({ providedIn: 'root' })
export class Catalogue {
  private readonly http = inject(HttpClient);

  // --- Import du catalogue ------------------------------------------------------------------

  /**
   * Envoie le classeur et rend ce qui entrerait, ou ce qui est entre.
   *
   * `simulation` vaut vrai par defaut cote serveur aussi : un appel maladroit montre sans ecrire.
   */
  importerArticles(fichier: File, simulation: boolean): Observable<RapportImport> {
    const corps = new FormData();
    corps.append('fichier', fichier);
    return this.http.post<RapportImport>(`${API}/articles/import`, corps, {
      params: { simulation },
    });
  }

  /** Le classeur vierge a remplir. `blob` et non JSON : c'est un fichier. */
  modeleArticles(): Observable<Blob> {
    return this.http.get(`${API}/articles/import/modele`, { responseType: 'blob' });
  }

  // --- Articles ---------------------------------------------------------------------------

  articles(q: string, idCategory: number | null, page = 0, taille = 25): Observable<Page<ArticleDto>> {
    const params: Record<string, string | number> = {
      q,
      page,
      size: taille,
      sort: 'designation,asc',
    };
    if (idCategory != null) {
      params['idCategory'] = idCategory;
    }
    return this.http.get<Page<ArticleDto>>(`${API}/articles`, { params });
  }

  enregistrerArticle(article: ArticleDto): Observable<ArticleDto> {
    return this.http.post<ArticleDto>(`${API}/articles/create`, article);
  }

  /** Le chemin est au singulier, contrairement aux autres : c'est ainsi que l'API l'expose. */
  supprimerArticle(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/article/delete/${id}`);
  }

  // --- Categories -------------------------------------------------------------------------

  categories(): Observable<CategoryDto[]> {
    return this.http.get<CategoryDto[]>(`${API}/category/all`);
  }

  enregistrerCategorie(categorie: CategoryDto): Observable<CategoryDto> {
    return this.http.post<CategoryDto>(`${API}/category/create`, categorie);
  }

  supprimerCategorie(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/category/delete/${id}`);
  }
}
