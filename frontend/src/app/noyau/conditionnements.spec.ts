import { describe, expect, it } from 'vitest';
import { enConditionnements, unites, vendables } from './conditionnements';

const coca = {
  uniteBase: 'PIECE' as const,
  conditionnements: [
    { id: 1, libelle: 'Pack de 6', quantiteUnites: 6, vendable: true, actif: true },
    { id: 2, libelle: 'Carton de 24', quantiteUnites: 24, vendable: true, actif: true },
    { id: 3, libelle: 'Palette', quantiteUnites: 1200, vendable: false, actif: false },
  ],
};

describe('enConditionnements', () => {
  it('lit la reserve dans le plus grand conditionnement actif', () => {
    expect(enConditionnements(coca, 269)).toBe('11 × Carton de 24 + 5 pièces');
  });

  it('ne dit rien quand la quantite ne remplit pas un conditionnement', () => {
    expect(enConditionnements(coca, 20)).toBeNull();
    expect(enConditionnements({ uniteBase: 'PIECE', conditionnements: [] }, 500)).toBeNull();
  });

  it('garde les decimales du vrac', () => {
    const riz = { uniteBase: 'KG' as const, conditionnements: [{ id: 4, libelle: 'Sac de 25 kg', quantiteUnites: 25 }] };
    expect(enConditionnements(riz, 178)).toBe('7 × Sac de 25 kg + 3 kg');
  });
});

describe('unites', () => {
  it('accorde la piece au nombre, pas le kilo', () => {
    expect(unites(coca, 1)).toBe('pièce');
    expect(unites(coca, 245)).toBe('pièces');
    expect(unites({ uniteBase: 'KG' }, 2.5)).toBe('kg');
  });
});

describe('vendables', () => {
  it('ecarte les conditionnements retires ou reserves a l’achat', () => {
    expect(vendables(coca).map((c) => c.id)).toEqual([1, 2]);
  });
});
