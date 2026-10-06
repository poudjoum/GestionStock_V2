import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import type { EntrepriseDto } from '../noyau/api';
import type { components } from '../api/schema';

const API = `${environnement.api}/gestiondestock/v1`;

/**
 * Les formes de la plateforme, lues dans la specification du serveur comme toutes les autres. Elles
 * etaient ecrites a la main en attendant que les routes soient deployees — c'est fait, et une copie
 * a la main aurait ignore en silence l'activite des commerces ajoutee depuis.
 */
export type CommerceDto = components['schemas']['CommerceDto'];
export type ResumePlateformeDto = components['schemas']['ResumePlateformeDto'];
export type StatutAbonnement = NonNullable<CommerceDto['statut']>;

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
