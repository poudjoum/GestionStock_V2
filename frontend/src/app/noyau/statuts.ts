import type { LigneInventaireDto } from './api';

export type StatutStock = NonNullable<LigneInventaireDto['statut']>;

/**
 * Les statuts dits par les composants du design system : un ton, une icone et un mot, pour
 * `<gs-statut>`. Une seule table par objet : deux finiraient par diverger, et le meme article
 * s'afficherait en orange ici et en rouge la.
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

export type StatutCampagne = 'A_VENIR' | 'EN_COURS' | 'TERMINEE' | 'ARRETEE';

/**
 * Les statuts d'une campagne. Le ton promotion pour celle qui tourne : c'est elle qui fait vendre,
 * et celle qu'on cherche des yeux dans la liste. Une campagne arretee n'est pas une alarme.
 */
export const STATUTS_CAMPAGNE: Record<StatutCampagne, StatutAffiche> = {
  EN_COURS: { libelle: 'En cours', ton: 'promo', icone: 'campaign' },
  A_VENIR: { libelle: 'À venir', ton: 'ok', icone: 'schedule' },
  TERMINEE: { libelle: 'Terminée', ton: 'neutre', icone: 'done' },
  ARRETEE: { libelle: 'Arrêtée', ton: 'neutre', icone: 'block' },
};
