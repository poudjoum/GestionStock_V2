import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, shareReplay, tap } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import type { EntrepriseDto } from './api';

const API = `${environnement.api}/gestiondestock/v1`;

/**
 * L'identite du magasin : ce que le ticket de caisse imprime en en-tete.
 *
 * Elle est chargee une fois et gardee : un nom et une adresse ne changent pas pendant une journee
 * de caisse, et le ticket en a besoin a chaque impression. Sans ce cache, chaque vente ajouterait
 * un aller-retour au moment ou le client attend son papier.
 */
@Injectable({ providedIn: 'root' })
export class Entreprise {
  private readonly http = inject(HttpClient);

  private readonly connue = signal<EntrepriseDto | null>(null);
  /** L'identite, si elle a deja ete chargee. Nulle avant le premier chargement. */
  readonly mienne = this.connue.asReadonly();

  /**
   * L'appel en cours, partage.
   *
   * Le comptoir et le ticket la demandent tous les deux au demarrage : sans cela, deux requetes
   * identiques partent en meme temps.
   */
  private enCours?: Observable<EntrepriseDto>;

  /** Charge l'identite si elle ne l'est pas deja. */
  charger(): Observable<EntrepriseDto> {
    const deja = this.connue();
    if (deja) {
      return of(deja);
    }
    this.enCours ??= this.http.get<EntrepriseDto>(`${API}/entreprises/mienne`).pipe(
      tap((entreprise) => this.connue.set(entreprise)),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.enCours;
  }

  /** Relit l'identite en ignorant ce qui est garde. */
  relire(): Observable<EntrepriseDto> {
    this.enCours = undefined;
    this.connue.set(null);
    return this.charger();
  }

  /**
   * Enregistre l'identite corrigee.
   *
   * L'identifiant present dans le corps est ignore par le serveur, qui ne modifie jamais que
   * l'entreprise du jeton. Ce qu'il rend remplace ce qui etait garde : un ticket imprime juste
   * apres porte la nouvelle adresse.
   */
  enregistrer(identite: EntrepriseDto): Observable<EntrepriseDto> {
    return this.http.put<EntrepriseDto>(`${API}/entreprises/mienne`, identite).pipe(
      tap((enregistree) => {
        this.connue.set(enregistree);
        this.enCours = of(enregistree);
      }),
    );
  }
}
