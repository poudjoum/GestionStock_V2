import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Accueil, EtatDeCaisseDto, EtatDuStockDto, FactureDto } from './accueil.service';
import { Session } from '../noyau/session';
import type { LigneInventaireDto } from '../noyau/api';
import { PASTILLES_STOCK } from '../noyau/statuts';

/** Les libelles des modes de reglement, qui arrivent en majuscules de l'API. */
const MODES: Record<string, string> = {
  ESPECES: 'Espèces',
  MOBILE_MONEY: 'Mobile Money',
  VIREMENT: 'Virement',
  CHEQUE: 'Chèque',
  CARTE: 'Carte',
};

/**
 * Ce qu'on voit en arrivant.
 *
 * Il manquait, et c'etait le principal reproche a faire a cette interface : un gerant se
 * connectait pour tomber sur un ecran de caisse. Ce qu'il veut savoir en ouvrant l'application,
 * c'est ce que vaut son magasin, ce qu'a encaisse sa caisse aujourd'hui, et ce qui manque.
 *
 * Aucune route nouvelle : `/stock/etat` et `/caisse/etat` existaient depuis leurs lots
 * respectifs, et personne ne les lisait.
 */
@Component({
  selector: 'app-tableau-de-bord',
  imports: [DecimalPipe, RouterLink, MatButtonModule, MatIconModule],
  templateUrl: './tableau-de-bord.html',
})
export class TableauDeBord implements OnInit {
  private readonly service = inject(Accueil);
  private readonly session = inject(Session);

  protected readonly stock = signal<EtatDuStockDto | null>(null);
  protected readonly caisse = signal<EtatDeCaisseDto | null>(null);
  protected readonly alertes = signal<LigneInventaireDto[]>([]);
  protected readonly factures = signal<FactureDto[]>([]);
  protected readonly chargement = signal(true);

  protected readonly prenom = this.session.username;
  protected readonly peutVoirLaCaisse = signal(false);
  protected readonly peutVoirLeStock = signal(false);

  protected readonly pastille = PASTILLES_STOCK;
  protected readonly modes = MODES;

  ngOnInit(): void {
    const roles = this.session.roles();
    // Chaque carte n'est demandee que si le role la voit : appeler une route qui rendra 403
    // remplirait la console d'erreurs et n'apprendrait rien a l'utilisateur.
    this.peutVoirLeStock.set(
      roles.some((r) =>
        ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER', 'ROLE_COMPTABLE'].includes(r),
      ),
    );
    this.peutVoirLaCaisse.set(
      roles.some((r) =>
        ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_CAISSIER', 'ROLE_COMPTABLE'].includes(r),
      ),
    );

    if (this.peutVoirLeStock()) {
      this.service.etatDuStock().subscribe({
        next: (etat) => this.stock.set(etat),
        error: () => undefined,
      });
      this.service.alertes().subscribe({
        next: (lignes) => this.alertes.set(lignes.slice(0, 6)),
        error: () => undefined,
      });
    }
    if (this.peutVoirLaCaisse()) {
      this.service.etatDeCaisse().subscribe({
        next: (etat) => this.caisse.set(etat),
        error: () => undefined,
      });
      this.service.dernieresFactures().subscribe({
        next: (page) => this.factures.set(page.content ?? []),
        error: () => undefined,
      });
    }
    // Le squelette disparait vite : chaque carte se remplit quand sa reponse arrive, et n'attend
    // pas les autres.
    setTimeout(() => this.chargement.set(false), 400);
  }

  protected salutation(): string {
    const heure = new Date().getHours();
    if (heure < 12) return 'Bonjour';
    if (heure < 18) return 'Bonne journée';
    return 'Bonsoir';
  }

  protected statutFacture(facture: FactureDto): { libelle: string; fond: string; texte: string } {
    switch (facture.statutReglement) {
      case 'REGLEE':
        return {
          libelle: 'Réglée',
          fond: 'var(--mat-sys-secondary-container)',
          texte: 'var(--mat-sys-on-secondary-container)',
        };
      case 'PARTIELLEMENT_REGLEE':
        return {
          libelle: 'Partielle',
          fond: 'var(--mat-sys-tertiary-container)',
          texte: 'var(--mat-sys-on-tertiary-container)',
        };
      default:
        return {
          libelle: 'Impayée',
          fond: 'var(--mat-sys-error-container)',
          texte: 'var(--mat-sys-on-error-container)',
        };
    }
  }
}
