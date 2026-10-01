import { describe, expect, it } from 'vitest';
import { montantsTendus } from './vente-au-comptoir';

describe('montantsTendus', () => {
  it('propose les coupures au-dessus d’un petit total', () => {
    expect(montantsTendus(4770)).toEqual([5000, 10000]);
  });

  it('propose des montants ronds au-delà de la plus grosse coupure', () => {
    expect(montantsTendus(23421)).toEqual([24000, 25000, 30000]);
  });

  it('ne propose rien de plus qu’un compte exact quand le total est rond', () => {
    expect(montantsTendus(30000)).toEqual([]);
  });

  it('ne propose rien pour un ticket nul', () => {
    expect(montantsTendus(0)).toEqual([]);
  });
});
