import { describe, expect, it } from 'vitest';
import { cle, imprimable, modules, svgDuCode } from './code-barres';

describe('code-barres', () => {
  it('calcule la cle comme le serveur', () => {
    expect(cle('544900000099')).toBe(6);
    expect(cle('400638133393')).toBe(1);
    expect(cle('9638507')).toBe(4);
  });

  it('dessine un EAN-13 sur 95 modules, garde, centre et fin compris', () => {
    const m = modules('5449000000996');
    expect(m).toHaveLength(95);
    expect(m.startsWith('101')).toBe(true);
    expect(m.endsWith('101')).toBe(true);
    expect(m.slice(45, 50)).toBe('01010');
  });

  it('encode la partie gauche selon la parite du premier chiffre', () => {
    // 4 : L G L L G G. Le premier chiffre code, « 0 », s'ecrit donc en L puis le second en G.
    const m = modules('4006381333931');
    expect(m.slice(3, 10)).toBe('0001101'); // 0 en L
    expect(m.slice(10, 17)).toBe('0100111'); // 0 en G
    // La partie droite est toujours en R : le « 1 » final de la cle.
    expect(m.slice(85, 92)).toBe('1100110');
  });

  it('chaque chiffre de droite a un nombre pair de barres, ce qui permet de lire dans les deux sens', () => {
    const m = modules('2000000000015');
    for (let i = 50; i < 92; i += 7) {
      expect([...m.slice(i, i + 7)].filter((b) => b === '1').length % 2).toBe(0);
    }
  });

  it('dessine un EAN-8 sur 67 modules, et un UPC-A comme un EAN-13', () => {
    expect(modules('96385074')).toHaveLength(67);
    expect(modules('036000291452')).toBe(modules('0036000291452'));
  });

  it('refuse ce qu’il ne sait pas dessiner', () => {
    expect(imprimable('REF-CIMENT')).toBe(false);
    expect(imprimable('15449000000993')).toBe(false);
    expect(() => modules('ABC')).toThrow();
  });

  it('produit un SVG avec les chiffres lisibles', () => {
    expect(svgDuCode('5449000000996')).toContain('5  449000  000996');
  });
});
