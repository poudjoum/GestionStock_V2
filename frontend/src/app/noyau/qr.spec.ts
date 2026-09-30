import { describe, expect, it } from 'vitest';
import jsQR from 'jsqr';
import { dessinerQr, matriceQr } from './qr';
import { adresseDuTicket, codeLisible, nouveauCodeDeTicket } from './code-ticket';

/**
 * Le QR du ticket, relu par un decodeur independant.
 *
 * Un QR faux ne se voit pas a l'oeil : il a l'air d'un QR, et c'est le telephone du client qui
 * refuse de le lire, au comptoir, devant lui. Chaque QR produit est donc rendu en pixels et relu
 * par `jsQR`, qui n'a rien en commun avec l'encodeur.
 */
function relire(texte: string): string | null {
  const matrice = matriceQr(texte);
  const marge = 4;
  const echelle = 4;
  const cote = (matrice.length + 2 * marge) * echelle;
  const pixels = new Uint8ClampedArray(cote * cote * 4).fill(255);
  matrice.forEach((ligne, l) =>
    ligne.forEach((sombre, c) => {
      if (!sombre) return;
      for (let dy = 0; dy < echelle; dy++) {
        for (let dx = 0; dx < echelle; dx++) {
          const x = (c + marge) * echelle + dx;
          const y = (l + marge) * echelle + dy;
          const i = (y * cote + x) * 4;
          pixels[i] = pixels[i + 1] = pixels[i + 2] = 0;
        }
      }
    }),
  );
  return jsQR(pixels, cote, cote)?.data ?? null;
}

describe('le QR du ticket', () => {
  it('se relit et redonne exactement l’adresse du ticket', () => {
    const adresse = adresseDuTicket('https://stock.tontinepro.uk', '7K3M9P2QA4TZ');
    expect(adresse).toBe('https://stock.tontinepro.uk/t/7K3M9P2QA4TZ');
    expect(relire(adresse)).toBe(adresse);
  });

  it('se relit pour cent codes tires au hasard', () => {
    for (let i = 0; i < 100; i++) {
      const adresse = adresseDuTicket('https://stock.tontinepro.uk/', nouveauCodeDeTicket());
      expect(relire(adresse)).toBe(adresse);
    }
  });

  it('reste assez petit pour un ticket de 72 mm', () => {
    // 33 modules : version 4. A 0,6 mm le module, moins de 2 cm de cote — marge comprise.
    expect(dessinerQr(adresseDuTicket('https://stock.tontinepro.uk', 'ZZZZZZZZZZZZ')).modules)
      .toBeLessThanOrEqual(33);
  });
});

describe('le code du ticket', () => {
  it('a douze caracteres de l’alphabet de Crockford', () => {
    for (let i = 0; i < 1000; i++) {
      expect(nouveauCodeDeTicket()).toMatch(/^[0-9A-HJKMNP-TV-Z]{12}$/);
    }
  });

  it('se lit en trois groupes de quatre', () => {
    expect(codeLisible('7K3M9P2QA4TZ')).toBe('7K3M-9P2Q-A4TZ');
  });
});
