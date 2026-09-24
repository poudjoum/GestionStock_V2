import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import type { components } from '../api/schema';
import type { ArticleDto, ClientDto, Page } from '../noyau/api';
import type { ReglementDto } from '../noyau/reglements';

export type VenteDto = components['schemas']['VenteDto'];
export type FactureDto = components['schemas']['FactureDto'];
export type { ModeReglement, ReglementDto } from '../noyau/reglements';

const API = `${environnement.api}/gestiondestock/v1`;

@Injectable({ providedIn: 'root' })
export class Comptoir {
  private readonly http = inject(HttpClient);

  /** Le catalogue, filtre : c'est ce que le caissier tape pour retrouver un article. */
  articles(q: string, page = 0, taille = 20): Observable<Page<ArticleDto>> {
    return this.http.get<Page<ArticleDto>>(`${API}/articles`, {
      params: { q, page, size: taille, sort: 'designation,asc' },
    });
  }

  /** Un article par son code exact : ce que rend une lecture de code-barres. */
  parCode(code: string): Observable<ArticleDto> {
    return this.http.get<ArticleDto>(`${API}/articles/code/${encodeURIComponent(code)}`);
  }

  clients(q: string): Observable<Page<ClientDto>> {
    return this.http.get<Page<ClientDto>>(`${API}/clients`, { params: { q, page: 0, size: 10 } });
  }

  stockReel(idArticle: number): Observable<number> {
    return this.http.get<number>(`${API}/mouvements/stockreel/${idArticle}`);
  }

  vendre(vente: VenteDto): Observable<VenteDto> {
    return this.http.post<VenteDto>(`${API}/ventes/create`, vente);
  }

  facturer(idVente: number): Observable<FactureDto> {
    return this.http.post<FactureDto>(`${API}/ventes/${idVente}/facture`, {});
  }

  /**
   * Enregistre l'encaissement de la facture.
   *
   * Le serveur refuse un montant qui depasse ce qui reste du : c'est lui qui tient la regle, et le
   * comptoir n'a pas a la redire — il envoie ce qu'il encaisse.
   */
  regler(idFacture: number, reglement: ReglementDto): Observable<ReglementDto> {
    return this.http.post<ReglementDto>(`${API}/factures/${idFacture}/reglements`, reglement);
  }
}
