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

export type StatutCampagne = 'A_VENIR' | 'EN_COURS' | 'TERMINEE' | 'ARRETEE';

/**
 * Les statuts d'une campagne. Le vert pour celle qui tourne : c'est elle qui fait vendre, et
 * celle qu'on cherche des yeux dans la liste.
 */
export const PASTILLES_CAMPAGNE: Record<StatutCampagne, Pastille> = {
  EN_COURS: {
    libelle: 'En cours',
    fond: 'var(--mat-sys-primary-container)',
    texte: 'var(--mat-sys-on-primary-container)',
    icone: 'campaign',
  },
  A_VENIR: {
    libelle: 'À venir',
    fond: 'var(--mat-sys-tertiary-container)',
    texte: 'var(--mat-sys-on-tertiary-container)',
    icone: 'schedule',
  },
  TERMINEE: {
    libelle: 'Terminée',
    fond: 'var(--mat-sys-surface-container-high)',
    texte: 'var(--mat-sys-on-surface-variant)',
    icone: 'done',
  },
  ARRETEE: {
    libelle: 'Arrêtée',
    fond: 'var(--mat-sys-error-container)',
    texte: 'var(--mat-sys-on-error-container)',
    icone: 'block',
  },
};

/**
 * Les statuts dits par les composants du design system : un ton, une icone et un mot, pour
 * `<gs-statut>`. Les tables `PASTILLES_*` ci-dessus restent pour les ecrans pas encore passes
 * aux composants.
 */
export interface StatutAffiche {
  libelle: string;
  ton: 'ok' | 'alerte' | 'danger' | 'promo' | 'neutre';
  icone: string;
}

/** Le stock d'un article. Le negatif et la rupture bloquent ; le seuil approche. */
export const STATUTS_STOCK: Record<StatutStock, StatutAffiche> = {
  NEGATIF: { libelle: 'Négatif', ton: 'danger', icone: 'priority_high' },
  RUPTURE: { libelle: 'Rupture', ton: 'danger', icone: 'remove_shopping_cart' },
  SOUS_SEUIL: { libelle: 'Sous le seuil', ton: 'alerte', icone: 'trending_down' },
  SUFFISANT: { libelle: 'Suffisant', ton: 'ok', icone: 'check' },
  SANS_SEUIL: { libelle: 'Non surveillé', ton: 'neutre', icone: 'remove' },
};

export function statutDuStock(ligne: { statut?: StatutStock | null }): StatutAffiche {
  return STATUTS_STOCK[ligne.statut ?? 'SANS_SEUIL'];
}

/**
 * Ce qu'il reste a encaisser sur une facture.
 *
 * Une facture impayee n'est pas une alarme : c'est le quotidien d'un commerce qui vend a credit.
 * Elle prend le ton « danger » pour qu'on la voie dans une liste, pas un bandeau rouge sur toute
 * la page.
 */
export function statutDeFacture(facture: {
  annulee?: boolean;
  statutReglement?: 'IMPAYEE' | 'PARTIELLEMENT_REGLEE' | 'REGLEE';
}): StatutAffiche {
  if (facture.annulee) {
    return { libelle: 'Annulée', ton: 'neutre', icone: 'block' };
  }
  switch (facture.statutReglement) {
    case 'REGLEE':
      return { libelle: 'Réglée', ton: 'ok', icone: 'check' };
    case 'PARTIELLEMENT_REGLEE':
      return { libelle: 'Partielle', ton: 'alerte', icone: 'hourglass_bottom' };
    default:
      return { libelle: 'Impayée', ton: 'danger', icone: 'schedule' };
  }
}
