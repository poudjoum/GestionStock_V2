import { describe, expect, it } from 'vitest';
import { codesDuGtin, lireGs1 } from './gs1';

const GS = '\u001d';

describe('lireGs1', () => {
  it('lit la forme lisible, avec parentheses', () => {
    expect(lireGs1('(01)03400935896744(17)271231(10)LOT42')).toEqual({
      gtin: '03400935896744',
      lot: 'LOT42',
      peremption: '2027-12-31',
      serie: null,
    });
  });

  it('lit la forme brute, le GS separant le lot de la serie', () => {
    expect(lireGs1(`010340093589674417270600` + `10A1B2${GS}21SN99`)).toEqual({
      gtin: '03400935896744',
      lot: 'A1B2',
      // Un jour « 00 » vaut le dernier jour du mois.
      peremption: '2027-06-30',
      serie: 'SN99',
    });
  });

  it('ignore l’identifiant de symbologie de la douchette', () => {
    expect(lireGs1(']d201034009358967441027A')?.lot).toBe('27A');
  });

  it('lit le lot jusqu’a la fin quand le GS s’est perdu et qu’il est le dernier champ', () => {
    expect(lireGs1('0103400935896744172801311029-X')?.lot).toBe('29-X');
  });

  it('saute un poids pour retrouver le lot', () => {
    expect(lireGs1(`0103400935896744310300125010L7`)?.lot).toBe('L7');
  });

  it('laisse passer un EAN-13 ordinaire, qui n’est pas un GS1', () => {
    expect(lireGs1('3017620422003')).toBeNull();
    expect(lireGs1('ART-001')).toBeNull();
  });
});

describe('codesDuGtin', () => {
  it('propose l’EAN-13 contenu dans le GTIN', () => {
    expect(codesDuGtin('03017620422003')).toEqual(['03017620422003', '3017620422003']);
  });
});
