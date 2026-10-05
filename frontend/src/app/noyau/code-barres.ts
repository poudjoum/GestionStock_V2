/**
 * Les codes EAN-13, EAN-8 et UPC-A, dessines pour une etiquette.
 *
 * Pas de bibliotheque : la symbologie tient en trois tables de dix motifs, et l'etiquette sort
 * en SVG, net a toutes les tailles d'impression. Les autres symbologies (Code 128, ITF-14) sont
 * celles des fabricants, deja imprimees sur la marchandise : le magasin n'a pas a les produire.
 */

/** Les motifs « L » (jeu A), sept modules par chiffre. R en est le complement, G le miroir de R. */
const L = ['0001101', '0011001', '0010011', '0111101', '0100011', '0110001', '0101111', '0111011', '0110111', '0001011'];
const R = L.map((m) => [...m].map((b) => (b === '1' ? '0' : '1')).join(''));
const G = R.map((m) => [...m].reverse().join(''));

/** Le premier chiffre d'un EAN-13 ne s'imprime pas en barres : il choisit la parite des six suivants. */
const PARITES = ['LLLLLL', 'LLGLGG', 'LLGGLG', 'LLGGGL', 'LGLLGG', 'LGGLLG', 'LGGGLL', 'LGLGLG', 'LGLGGL', 'LGGLGL'];

const GARDE = '101';
const CENTRE = '01010';

/** La cle de controle, ponderee 3-1 depuis la droite : la regle du serveur. */
export function cle(chiffresSansCle: string): number {
  let somme = 0;
  for (let i = 0; i < chiffresSansCle.length; i++) {
    const chiffre = Number(chiffresSansCle[chiffresSansCle.length - 1 - i]);
    somme += i % 2 === 0 ? chiffre * 3 : chiffre;
  }
  return (10 - (somme % 10)) % 10;
}

/** Un code qu'on sait dessiner : EAN-13, EAN-8, ou UPC-A, qui s'imprime comme un EAN-13 commencant par 0. */
export function imprimable(code: string): boolean {
  return /^\d+$/.test(code) && [8, 12, 13].includes(code.length);
}

/**
 * Les modules du code, de gauche a droite : « 1 » une barre, « 0 » un blanc. 95 modules pour un
 * EAN-13, 67 pour un EAN-8 — sans les marges blanches, que le dessin ajoute.
 */
export function modules(code: string): string {
  if (!imprimable(code)) {
    throw new Error(`Code non imprimable : ${code}`);
  }
  const ean = code.length === 12 ? `0${code}` : code;
  if (ean.length === 8) {
    const gauche = [...ean.slice(0, 4)].map((c) => L[Number(c)]).join('');
    const droite = [...ean.slice(4)].map((c) => R[Number(c)]).join('');
    return GARDE + gauche + CENTRE + droite + GARDE;
  }
  const parite = PARITES[Number(ean[0])];
  const gauche = [...ean.slice(1, 7)].map((c, i) => (parite[i] === 'L' ? L : G)[Number(c)]).join('');
  const droite = [...ean.slice(7)].map((c) => R[Number(c)]).join('');
  return GARDE + gauche + CENTRE + droite + GARDE;
}

/**
 * Le code en SVG, chiffres lisibles dessous. `largeurModule` est en millimetres : 0,33 mm est la
 * taille nominale d'un EAN-13 (37,3 mm de large) ; on peut descendre a 80 % sans gener la lecture.
 */
export function svgDuCode(code: string, largeurModule = 0.3, hauteur = 18): string {
  const barres = modules(code);
  const ean = code.length === 12 ? `0${code}` : code;
  const marge = 9; // la zone blanche que la douchette exige de chaque cote, en modules
  const total = barres.length + marge * 2;
  const largeur = total * largeurModule;
  const texte = 3; // hauteur des chiffres, en mm
  const rectangles: string[] = [];
  for (let i = 0; i < barres.length; ) {
    if (barres[i] === '1') {
      let fin = i;
      while (barres[fin] === '1') fin++;
      rectangles.push(`<rect x="${(marge + i) * largeurModule}" y="0" width="${(fin - i) * largeurModule}" height="${hauteur}"/>`);
      i = fin;
    } else {
      i++;
    }
  }
  const lisible =
    ean.length === 13
      ? `${ean[0]}  ${ean.slice(1, 7)}  ${ean.slice(7)}`
      : `${ean.slice(0, 4)}  ${ean.slice(4)}`;
  return (
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${largeur} ${hauteur + texte + 0.6}" ` +
    `width="${largeur}mm" height="${hauteur + texte + 0.6}mm" role="img" aria-label="Code-barres ${code}">` +
    `<rect width="100%" height="100%" fill="#fff"/>` +
    `<g fill="#000">${rectangles.join('')}</g>` +
    `<text x="${largeur / 2}" y="${hauteur + texte}" font-family="monospace" font-size="${texte}" text-anchor="middle">${lisible}</text>` +
    `</svg>`
  );
}
