import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import type { components } from '../api/schema';
import type { Page } from '../noyau/api';

export type CommandeClientDto = components['schemas']['CommandeClientDto'];
export type LigneCommandeClientDto = components['schemas']['LigneCommandeClientDto'];
export type VenteDto = components['schemas']['VenteDto'];
export type EtatCommande = NonNullable<CommandeClientDto['etat']>;

const RACINE = `${environnement.api}/gestiondestock/v1/commandes-clients`;

/** Ce qui se compose encore : rien n'est reserve. */
export const EN_PREPARATION: EtatCommande[] = ['EN_PREPARATION'];
/** Ce qui est promis au client : la marchandise est reservee, et attend d'etre servie. */
export const A_SERVIR: EtatCommande[] = ['VALIDEE', 'PARTIELLEMENT_LIVREE'];
/** L'historique : servie, close ou annulee. */
export const TERMINEES: EtatCommande[] = ['LIVREE', 'CLOTUREE', 'ANNULEE'];

@Injectable({ providedIn: 'root' })
export class CommandesClients {
  private readonly http = inject(HttpClient);

  lister(etats: EtatCommande[], q: string, page = 0, taille = 25): Observable<Page<CommandeClientDto>> {
    return this.http.get<Page<CommandeClientDto>>(RACINE, {
      params: { etat: etats, q, page, size: taille, sort: 'id,desc' },
    });
  }

  detail(id: number): Observable<CommandeClientDto> {
    return this.http.get<CommandeClientDto>(`${RACINE}/${id}`);
  }

  lignes(id: number): Observable<LigneCommandeClientDto[]> {
    return this.http.get<LigneCommandeClientDto[]>(`${RACINE}/${id}/lignes`);
  }

  /** La commande et ses lignes d'un seul envoi : l'API les ecrit dans la meme transaction. */
  creer(commande: CommandeClientDto): Observable<CommandeClientDto> {
    return this.http.post<CommandeClientDto>(`${RACINE}/create`, commande);
  }

  ajouterLigne(id: number, ligne: LigneCommandeClientDto): Observable<LigneCommandeClientDto> {
    return this.http.post<LigneCommandeClientDto>(`${RACINE}/${id}/lignes`, ligne);
  }

  modifierQuantite(id: number, idLigne: number, quantite: number): Observable<LigneCommandeClientDto> {
    return this.http.patch<LigneCommandeClientDto>(`${RACINE}/${id}/lignes/${idLigne}`, null, {
      params: { quantite },
    });
  }

  retirerLigne(id: number, idLigne: number): Observable<void> {
    return this.http.delete<void>(`${RACINE}/${id}/lignes/${idLigne}`);
  }

  /** Valider reserve la marchandise dans le site qui livre. */
  changerEtat(id: number, etat: 'VALIDEE' | 'ANNULEE'): Observable<CommandeClientDto> {
    return this.http.patch<CommandeClientDto>(`${RACINE}/${id}/etat/${etat}`, null);
  }

  /** Le site qui livre : le magasin, ou l'entrepot quand le magasin n'a pas de quoi. */
  changerSiteExpedition(id: number, idSite: number): Observable<CommandeClientDto> {
    return this.http.patch<CommandeClientDto>(`${RACINE}/${id}/expedition/${idSite}`, null);
  }

  /** Sert ce qui est demande, ligne par ligne ; une liste vide sert tout le reliquat. */
  servir(id: number, lignes: { idLigne: number; quantite: number }[]): Observable<VenteDto> {
    return this.http.post<VenteDto>(`${RACINE}/${id}/vente-partielle`, lignes);
  }

  /** On cesse de devoir le reliquat au client. Le motif est exige. */
  cloturer(id: number, motif: string): Observable<CommandeClientDto> {
    return this.http.post<CommandeClientDto>(`${RACINE}/${id}/cloture`, { motif });
  }
}
