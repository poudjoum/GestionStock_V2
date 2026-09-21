import type { LigneInventaireDto } from './api';

export type StatutStock = NonNullable<LigneInventaireDto['statut']>;

export interface Pastille {
  libelle: string;
  fond: string;
  texte: string;
  icone: string;
}

/**
 * Ce que chaque statut dit, et de quelle couleur on le dit.
 *
 * Partage entre le tableau de bord et l'ecran de stock : deux tables de couleurs finiraient par
 * diverger, et le meme article s'afficherait en orange ici et en rouge la.
 *
 * Le negatif prend la couleur d'erreur pleine, et non son conteneur : il ne demande pas une
 * commande au fournisseur mais un comptage sur l'etagere, et c'est le seul statut qui signale une
 * incoherence plutot qu'un manque.
 */
export const PASTILLES_STOCK: Record<StatutStock, Pastille> = {
  NEGATIF: {
    libelle: 'Négatif',
    fond: 'var(--mat-sys-error)',
    texte: 'var(--mat-sys-on-error)',
    icone: 'priority_high',
  },
  RUPTURE: {
    libelle: 'Rupture',
    fond: 'var(--mat-sys-error-container)',
    texte: 'var(--mat-sys-on-error-container)',
    icone: 'remove_shopping_cart',
  },
  SOUS_SEUIL: {
    libelle: 'Sous le seuil',
    fond: 'var(--mat-sys-tertiary-container)',
    texte: 'var(--mat-sys-on-tertiary-container)',
    icone: 'trending_down',
  },
  SUFFISANT: {
    libelle: 'Suffisant',
    fond: 'var(--mat-sys-secondary-container)',
    texte: 'var(--mat-sys-on-secondary-container)',
    icone: 'check',
  },
  SANS_SEUIL: {
    libelle: 'Non surveillé',
    fond: 'var(--mat-sys-surface-container-high)',
    texte: 'var(--mat-sys-on-surface-variant)',
    icone: 'remove',
  },
};

export function pastilleDe(ligne: LigneInventaireDto): Pastille {
  return PASTILLES_STOCK[ligne.statut ?? 'SANS_SEUIL'];
}
