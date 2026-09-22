import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import type { components } from '../api/schema';
import type { Page } from '../noyau/api';

export type CommandeFourDto = components['schemas']['CommandeFourDto'];
export type LigneCmndeFournisseurDto = components['schemas']['LigneCmndeFournisseurDto'];
export type LigneReceptionDto = components['schemas']['LigneReceptionDto'];
export type ArticleDto = components['schemas']['ArticleDto'];
export type EtatCommande = NonNullable<CommandeFourDto['etat']>;
export type { Tiers } from '../repertoire/repertoire.service';

const RACINE = `${environnement.api}/gestiondestock/v1/commandes-fournisseurs`;

/** Ce qui n'est pas encore parti chez le fournisseur : on peut encore y toucher. */
export const BROUILLONS: EtatCommande[] = ['EN_PREPARATION'];

/** Ce qui est parti et dont on attend la marchandise. */
export const A_RECEVOIR: EtatCommande[] = ['VALIDEE', 'PARTIELLEMENT_LIVREE'];

@Injectable({ providedIn: 'root' })
export class Achats {
  private readonly http = inject(HttpClient);

  lister(etats: EtatCommande[], q: string, page = 0, taille = 25): Observable<Page<CommandeFourDto>> {
    return this.http.get<Page<CommandeFourDto>>(RACINE, {
      params: { etat: etats, q, page, size: taille },
    });
  }

  detail(id: number): Observable<CommandeFourDto> {
    return this.http.get<CommandeFourDto>(`${RACINE}/${id}`);
  }

  lignes(id: number): Observable<LigneCmndeFournisseurDto[]> {
    return this.http.get<LigneCmndeFournisseurDto[]>(`${RACINE}/${id}/lignes`);
  }

  /**
   * Cree la commande et ses lignes d'un coup.
   *
   * L'API les ecrit dans la meme transaction : une commande a moitie enregistree, avec deux
   * lignes sur cinq, serait plus difficile a rattraper qu'une commande absente.
   */
  creer(commande: CommandeFourDto): Observable<CommandeFourDto> {
    return this.http.post<CommandeFourDto>(`${RACINE}/create`, commande);
  }

  // --- Les lignes d'un brouillon deja enregistre ---------------------------------------------
  //
  // Elles se modifient une par une, et non en renvoyant la commande entiere : c'est le contrat de
  // l'API, et cela evite qu'un envoi concurrent efface ce que l'autre vient d'ajouter.

  ajouterLigne(id: number, ligne: LigneCmndeFournisseurDto): Observable<LigneCmndeFournisseurDto> {
    return this.http.post<LigneCmndeFournisseurDto>(`${RACINE}/${id}/lignes`, ligne);
  }

  /**
   * Corrige une ligne : la quantite, le prix d'achat, ou les deux.
   *
   * Le prix compte autant que la quantite — c'est lui qui alimente le cout moyen de l'article,
   * donc la valeur du magasin. On n'envoie que ce qui change.
   */
  modifierLigne(
    id: number,
    idLigne: number,
    changement: { quantite?: number; prixUnitaire?: number },
  ): Observable<LigneCmndeFournisseurDto> {
    const params: Record<string, number> = {};
    if (changement.quantite != null) {
      params['quantite'] = changement.quantite;
    }
    if (changement.prixUnitaire != null) {
      params['prixUnitaire'] = changement.prixUnitaire;
    }
    return this.http.patch<LigneCmndeFournisseurDto>(`${RACINE}/${id}/lignes/${idLigne}`, null, {
      params,
    });
  }

  retirerLigne(id: number, idLigne: number): Observable<void> {
    return this.http.delete<void>(`${RACINE}/${id}/lignes/${idLigne}`);
  }

  /** Valider, c'est passer la commande au fournisseur : les lignes ne bougent plus ensuite. */
  valider(id: number): Observable<CommandeFourDto> {
    return this.http.patch<CommandeFourDto>(`${RACINE}/${id}/etat/VALIDEE`, null);
  }

  annuler(id: number): Observable<CommandeFourDto> {
    return this.http.patch<CommandeFourDto>(`${RACINE}/${id}/etat/ANNULEE`, null);
  }

  recevoir(id: number, receptions: LigneReceptionDto[]): Observable<CommandeFourDto> {
    return this.http.post<CommandeFourDto>(`${RACINE}/${id}/receptions`, receptions);
  }

  /** Solder un reliquat qui n'arrivera jamais. Le motif est exige par l'API. */
  cloturer(id: number, motif: string): Observable<CommandeFourDto> {
    return this.http.post<CommandeFourDto>(`${RACINE}/${id}/cloture`, { motif });
  }
}
