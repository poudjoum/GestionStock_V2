import { describe, expect, it } from 'vitest';
import { jourLocal, prixPromotionnel, promotionEnCours, PromotionArticleDto } from './prix-promotionnel';

describe('prixPromotionnel', () => {
  it('reduit d’un pourcentage, arrondi au centime comme le serveur', () => {
    expect(prixPromotionnel(5000, 'POURCENTAGE', 20)).toBe(4000);
    expect(prixPromotionnel(999, 'POURCENTAGE', 33)).toBe(669.33);
  });

  it('ne rend jamais l’article plus cher qu’au prix normal', () => {
    expect(prixPromotionnel(4000, 'PRIX_FIXE', 4500)).toBe(4000);
    expect(prixPromotionnel(5000, 'PRIX_FIXE', 4500)).toBe(4500);
  });
});

describe('promotionEnCours', () => {
  const promo = { dateDebut: '2026-10-01', dateFin: '2026-10-15' } as PromotionArticleDto;

  it('vaut du premier au dernier jour compris', () => {
    expect(promotionEnCours(promo, '2026-10-01')).toBe(true);
    expect(promotionEnCours(promo, '2026-10-15')).toBe(true);
    expect(promotionEnCours(promo, '2026-10-16')).toBe(false);
    expect(promotionEnCours(promo, '2026-09-30')).toBe(false);
  });

  it('lit le jour a l’heure de l’appareil', () => {
    expect(jourLocal(new Date(2026, 9, 5, 0, 30))).toBe('2026-10-05');
  });
});
