import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import type { EntrepriseDto } from '../noyau/api';

const API = `${environnement.api}/gestiondestock/v1`;

/**
 * Les formes de la plateforme.
 *
 * Ecrites a la main, et c'est provisoire : `npm run api:types` lit la specification du serveur
 * deploye, qui ne connait pas encore ces routes. Elles descendront dans `api/schema.d.ts` a la
 * prochaine regeneration, et ce bloc pourra tomber.
 */
export type StatutAbonnement = 'ACTIF' | 'ECHU' | 'SUSPENDU';

export interface CommerceDto {
  id?: number;
  nom?: string;
  ville?: string;
  tel?: string;
  email?: string;
  abonnementEcheance?: string;
  suspendue?: boolean;
  statut?: StatutAbonnement;
  /** Négatif quand l'échéance est passée, nul quand il n'y a pas d'abonnement. */
  joursRestants?: number | null;
  comptes?: number;
  articles?: number;
  ventes?: number;
  chiffreFacture?: number;
  derniereVente?: string;
}

export interface ResumePlateformeDto {
  commerces?: number;
  actifs?: number;
  echus?: number;
  suspendus?: number;
  aRelancer?: number;
  comptes?: number;
  ventes?: number;
  chiffreFacture?: number;
}

/** Ce qu'il faut pour ouvrir un commerce : la maison, et la personne qui y entrera. */
export interface InscriptionDto {
  entreprise: EntrepriseDto;
  administrateur: {
    nom?: string;
    prenoms?: string;
    username?: string;
    email?: string;
    motdepasse?: string;
    numTel?: string;
    dateNaissance?: string;
    adresse?: EntrepriseDto['adresse'];
  };
}

@Injectable({ providedIn: 'root' })
export class Plateforme {
  private readonly http = inject(HttpClient);

  commerces(): Observable<CommerceDto[]> {
    return this.http.get<CommerceDto[]>(`${API}/plateforme/commerces`);
  }

  resume(): Observable<ResumePlateformeDto> {
    return this.http.get<ResumePlateformeDto>(`${API}/plateforme/resume`);
  }

  /** Crée l'entreprise et son premier administrateur, et lui envoie ses accès par courriel. */
  inscrire(inscription: InscriptionDto): Observable<EntrepriseDto> {
    return this.http.post<EntrepriseDto>(`${API}/entreprises/inscription`, inscription);
  }

  suspendre(id: number): Observable<CommerceDto> {
    return this.http.post<CommerceDto>(`${API}/plateforme/commerces/${id}/suspension`, {});
  }

  reprendre(id: number): Observable<CommerceDto> {
    return this.http.post<CommerceDto>(`${API}/plateforme/commerces/${id}/reprise`, {});
  }

  renouveler(id: number): Observable<CommerceDto> {
    return this.http.post<CommerceDto>(`${API}/plateforme/commerces/${id}/renouvellement`, {});
  }

  /** Sans date, le commerce cesse d'être soumis à l'abonnement. */
  fixerEcheance(id: number, echeance: string | null): Observable<CommerceDto> {
    return this.http.post<CommerceDto>(
      `${API}/plateforme/commerces/${id}/echeance`,
      {},
      { params: echeance ? { echeance } : {} },
    );
  }
}
