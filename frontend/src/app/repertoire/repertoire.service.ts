import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import type { ClientDto, Page } from '../noyau/api';
import type { components } from '../api/schema';

export type FournisseurDto = components['schemas']['FournisseurDto'];
export type AdresseDto = components['schemas']['AdresseDto'];

/** Un client ou un fournisseur : les deux ecrans sont le meme, les deux metiers ne le sont pas. */
export type Genre = 'client' | 'fournisseur';

/**
 * La forme commune aux deux.
 *
 * L'API nomme les memes champs differemment — `prenoms` et `numTel` chez le client, `prenom` et
 * `tel` chez le fournisseur. Plutot que d'ecrire deux fois le meme formulaire et de les laisser
 * diverger, on traduit ici, en un seul endroit.
 */
export interface Tiers {
  id?: number;
  nom: string;
  prenom: string;
  mail: string;
  tel: string;
  adresse?: AdresseDto;
}

const API = `${environnement.api}/gestiondestock/v1`;

/** Les chemins different aussi : `clients` au pluriel, `fournisseur` au singulier. */
function racine(genre: Genre): string {
  return genre === 'client' ? `${API}/clients` : `${API}/fournisseur`;
}

function versTiers(genre: Genre, brut: ClientDto | FournisseurDto): Tiers {
  if (genre === 'client') {
    const c = brut as ClientDto;
    return {
      id: c.id,
      nom: c.nom ?? '',
      prenom: c.prenoms ?? '',
      mail: c.mail ?? '',
      tel: c.numTel ?? '',
      adresse: c.adresse,
    };
  }
  const f = brut as FournisseurDto;
  return {
    id: f.id,
    nom: f.nom ?? '',
    prenom: f.prenom ?? '',
    mail: f.mail ?? '',
    tel: f.tel ?? '',
    adresse: f.adresse,
  };
}

function versApi(genre: Genre, t: Tiers): ClientDto | FournisseurDto {
  const commun = { id: t.id, nom: t.nom, mail: t.mail, adresse: t.adresse };
  return genre === 'client'
    ? ({ ...commun, prenoms: t.prenom, numTel: t.tel } as ClientDto)
    : ({ ...commun, prenom: t.prenom, tel: t.tel } as FournisseurDto);
}

@Injectable({ providedIn: 'root' })
export class Repertoire {
  private readonly http = inject(HttpClient);

  lister(genre: Genre, q: string, page = 0, taille = 25): Observable<Page<Tiers>> {
    return this.http
      .get<Page<ClientDto | FournisseurDto>>(racine(genre), {
        params: { q, page, size: taille, sort: 'nom,asc' },
      })
      .pipe(
        map((p) => ({ ...p, content: (p.content ?? []).map((b) => versTiers(genre, b)) })),
      );
  }

  enregistrer(genre: Genre, tiers: Tiers): Observable<Tiers> {
    return this.http
      .post<ClientDto | FournisseurDto>(`${racine(genre)}/create`, versApi(genre, tiers))
      .pipe(map((b) => versTiers(genre, b)));
  }

  supprimer(genre: Genre, id: number): Observable<void> {
    return this.http.delete<void>(`${racine(genre)}/delete/${id}`);
  }
}
