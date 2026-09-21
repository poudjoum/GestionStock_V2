import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import type { components } from '../api/schema';
import type { Page } from '../noyau/api';

export type CommandeFourDto = components['schemas']['CommandeFourDto'];
export type LigneCmndeFournisseurDto = components['schemas']['LigneCmndeFournisseurDto'];
export type LigneReceptionDto = components['schemas']['LigneReceptionDto'];

const RACINE = `${environnement.api}/gestiondestock/v1/commandes-fournisseurs`;

/** Les etats dans lesquels une commande peut encore recevoir de la marchandise. */
const A_RECEVOIR = ['VALIDEE', 'PARTIELLEMENT_LIVREE'];

@Injectable({ providedIn: 'root' })
export class Receptions {
  private readonly http = inject(HttpClient);

  /**
   * Les commandes que le quai peut recevoir.
   *
   * Filtrees sur l'etat cote serveur : une commande encore en preparation n'a pas ete passee au
   * fournisseur, rien ne peut en arriver, et la montrer ici ne ferait qu'encombrer.
   */
  aRecevoir(q: string, page = 0, taille = 25): Observable<Page<CommandeFourDto>> {
    return this.http.get<Page<CommandeFourDto>>(RACINE, {
      params: { etat: A_RECEVOIR, q, page, size: taille },
    });
  }

  lignes(idCommande: number): Observable<LigneCmndeFournisseurDto[]> {
    return this.http.get<LigneCmndeFournisseurDto[]>(`${RACINE}/${idCommande}/lignes`);
  }

  /** Enregistre ce qui est reellement arrive. L'etat de la commande se deduit du reste attendu. */
  recevoir(idCommande: number, receptions: LigneReceptionDto[]): Observable<CommandeFourDto> {
    return this.http.post<CommandeFourDto>(`${RACINE}/${idCommande}/receptions`, receptions);
  }
}
