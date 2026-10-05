import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import type { ArticleDto, Page } from '../noyau/api';
import type { components } from '../api/schema';
import type { CodeBarresDto, ConditionnementDto } from '../noyau/conditionnements';

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
  /** Les conditionnements de la feuille du meme nom ; zero sans elle, et pour les repertoires. */
  conditionnements?: number;
}

@Injectable({ providedIn: 'root' })
export class Catalogue {
  private readonly http = inject(HttpClient);

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

  // --- Conditionnements et codes-barres ---------------------------------------------------

  conditionnements(idArticle: number): Observable<ConditionnementDto[]> {
    return this.http.get<ConditionnementDto[]>(`${API}/articles/${idArticle}/conditionnements`);
  }

  ajouterConditionnement(idArticle: number, c: ConditionnementDto): Observable<ConditionnementDto> {
    return this.http.post<ConditionnementDto>(`${API}/articles/${idArticle}/conditionnements`, c);
  }

  modifierConditionnement(idArticle: number, c: ConditionnementDto): Observable<ConditionnementDto> {
    return this.http.put<ConditionnementDto>(`${API}/articles/${idArticle}/conditionnements/${c.id}`, c);
  }

  retirerConditionnement(idArticle: number, idConditionnement: number): Observable<void> {
    return this.http.delete<void>(`${API}/articles/${idArticle}/conditionnements/${idConditionnement}`);
  }

  codes(idArticle: number): Observable<CodeBarresDto[]> {
    return this.http.get<CodeBarresDto[]>(`${API}/articles/${idArticle}/codes-barres`);
  }

  ajouterCode(idArticle: number, code: CodeBarresDto): Observable<CodeBarresDto> {
    return this.http.post<CodeBarresDto>(`${API}/articles/${idArticle}/codes-barres`, code);
  }

  genererCodeInterne(idArticle: number, idConditionnement: number | null): Observable<CodeBarresDto> {
    const params: Record<string, number> = idConditionnement == null ? {} : { idConditionnement };
    return this.http.post<CodeBarresDto>(`${API}/articles/${idArticle}/codes-barres/interne`, null, { params });
  }

  retirerCode(idArticle: number, idCode: number): Observable<void> {
    return this.http.delete<void>(`${API}/articles/${idArticle}/codes-barres/${idCode}`);
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
