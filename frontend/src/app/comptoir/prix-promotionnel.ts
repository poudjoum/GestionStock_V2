/**
 * Un article en promotion, tel que le serveur le donne a la caisse.
 *
 * Ecrit a la main, en attendant la regeneration des types contre l'API deployee.
 */
export interface PromotionArticleDto {
  idArticle: number;
  typeRemise: 'POURCENTAGE' | 'PRIX_FIXE';
  valeur: number;
  codeArticle?: string;
  designation?: string;
  photo?: string;
  prixNormalHt?: number;
  prixPromoHt?: number;
  tauxTva?: number;
  idCampagne?: number;
  titreCampagne?: string;
  /** Les jours du magasin, « 2026-10-01 », bornes comprises. */
  dateDebut: string;
  dateFin: string;
}

/** Une campagne du jour, telle que la caisse la garde. */
export interface CampagneDuJour {
  id: number;
  titre: string;
  dateDebut: string;
  dateFin: string;
}

/** Ce que la caisse garde de la journee : les campagnes en cours, et leurs articles reduits. */
export interface PromotionsDuJourDto {
  campagnes: CampagneDuJour[];
  promotions: PromotionArticleDto[];
}

/**
 * Le prix reduit, hors taxes : la regle de `PrixPromotionnel` cote serveur, recopiee a
 * l'identique parce que la caisse vend aussi hors ligne. Jamais plus cher que le prix normal.
 */
export function prixPromotionnel(
  prixNormalHt: number,
  type: PromotionArticleDto['typeRemise'],
  valeur: number,
): number {
  const reduit =
    type === 'POURCENTAGE'
      ? Math.round(((prixNormalHt * (100 - valeur)) / 100) * 100) / 100
      : valeur;
  return Math.min(reduit, prixNormalHt);
}

/** Le jour, a l'heure de l'appareil — celle du magasin —, au format des dates du serveur. */
export function jourLocal(maintenant = new Date()): string {
  const deux = (n: number) => String(n).padStart(2, '0');
  return `${maintenant.getFullYear()}-${deux(maintenant.getMonth() + 1)}-${deux(maintenant.getDate())}`;
}

/** Si la promotion vaut ce jour-la. Les dates ISO se comparent comme des chaines. */
export function promotionEnCours(
  promotion: { dateDebut: string; dateFin: string },
  jour: string,
): boolean {
  return promotion.dateDebut <= jour && jour <= promotion.dateFin;
}
