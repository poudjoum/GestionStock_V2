/**
 * La lecture d'un code GS1 : le DataMatrix ou le GS1-128 des emballages alimentaires et
 * pharmaceutiques, qui porte le produit, son lot et sa date.
 *
 * Un tel code est une suite de champs, chacun annonce par un identifiant d'application (AI) :
 * `01` le GTIN du produit, `10` le numero de lot, `17` la date limite, `21` le numero de serie.
 * Certains champs ont une longueur fixe ; les autres — le lot, la serie — s'arretent au caractere
 * GS (0x1D), ou a la fin du code.
 *
 * Trois formes arrivent au comptoir :
 * - la forme lisible, avec parentheses : `(01)03400935896744(17)271231(10)LOT42` ;
 * - la forme brute, telle qu'une douchette la transmet : `0103400935896744172712311 0LOT42`
 *   avec un GS apres chaque champ variable qui n'est pas le dernier ;
 * - l'une ou l'autre precedee de l'identifiant de symbologie (`]d2`, `]C1`, `]Q3`).
 *
 * Une douchette en mode clavier perd parfois le GS. Le lot est alors lu jusqu'a la fin du code :
 * juste quand il est le dernier champ — le cas le plus courant —, faux s'il est suivi d'un autre.
 * D'ou le reglage a demander a l'installateur : transmettre le FNC1 comme GS.
 */

export interface Gs1 {
  /** Le GTIN tel qu'imprime : 14 chiffres. */
  gtin: string;
  lot: string | null;
  /** AAAA-MM-JJ ; nulle sans AI 17. */
  peremption: string | null;
  serie: string | null;
}

const GS = '\u001d';

/** Les AI de longueur fixe que l'on rencontre sur un emballage, et la longueur de leur valeur. */
const FIXES: Record<string, number> = {
  '00': 18,
  '01': 14,
  '02': 14,
  '11': 6,
  '12': 6,
  '13': 6,
  '15': 6,
  '16': 6,
  '17': 6,
  '20': 2,
};

/** Les AI de longueur variable a deux chiffres, que termine un GS. */
const VARIABLES = new Set(['10', '21', '22', '30', '37']);

/**
 * Le code lu, s'il est un GS1 portant un GTIN ; `null` sinon — un EAN-13 ordinaire, un code
 * interne, qui suivent leur chemin habituel.
 */
export function lireGs1(brut: string): Gs1 | null {
  let code = brut.trim().replace(/^\][A-Za-z]\d/, '');
  if (code.startsWith('(')) {
    code = depuisParentheses(code);
  }
  const champs = new Map<string, string>();
  let i = 0;
  while (i < code.length) {
    if (code[i] === GS) {
      i++;
      continue;
    }
    const ai2 = code.slice(i, i + 2);
    const ai4 = code.slice(i, i + 4);
    if (ai2 in FIXES) {
      const valeur = code.slice(i + 2, i + 2 + FIXES[ai2]);
      if (valeur.length < FIXES[ai2]) return null;
      champs.set(ai2, valeur);
      i += 2 + FIXES[ai2];
    } else if (/^3[1-6]\d\d$/.test(ai4)) {
      // Poids et mesures : quatre chiffres d'AI, six de valeur. Lus pour etre sautes.
      i += 10;
    } else if (VARIABLES.has(ai2)) {
      const fin = code.indexOf(GS, i + 2);
      const valeur = code.slice(i + 2, fin === -1 ? undefined : fin);
      champs.set(ai2, valeur.slice(0, 30));
      i = fin === -1 ? code.length : fin + 1;
    } else {
      // Un AI qu'on ne connait pas : on ne sait pas ou il finit, on s'arrete la.
      break;
    }
  }
  const gtin = champs.get('01') ?? champs.get('02');
  if (!gtin || !/^\d{14}$/.test(gtin)) {
    return null;
  }
  return {
    gtin,
    lot: champs.get('10')?.trim() || null,
    peremption: dateGs1(champs.get('17')),
    serie: champs.get('21')?.trim() || null,
  };
}

/**
 * Les codes sous lesquels le produit peut etre enregistre au catalogue : le GTIN-14 tel quel,
 * puis l'EAN-13 et l'UPC-A qu'il contient — un article etiquete en EAN-13 a le meme GTIN, precede
 * d'un zero.
 */
export function codesDuGtin(gtin: string): string[] {
  const codes = [gtin];
  if (gtin.startsWith('0')) codes.push(gtin.slice(1));
  if (gtin.startsWith('00')) codes.push(gtin.slice(2));
  return codes;
}

/** `(01)0340…(10)LOT` devient `010340…10LOT` avec un GS apres chaque champ variable. */
function depuisParentheses(code: string): string {
  let resultat = '';
  const motif = /\((\d{2,4})\)([^(]*)/g;
  for (const [, ai, valeur] of code.matchAll(motif)) {
    resultat += ai + valeur + (VARIABLES.has(ai) ? GS : '');
  }
  return resultat;
}

/** AAMMJJ en AAAA-MM-JJ. Un jour « 00 » vaut le dernier jour du mois, comme le veut la norme. */
function dateGs1(valeur: string | undefined): string | null {
  if (!valeur || !/^\d{6}$/.test(valeur)) {
    return null;
  }
  const annee = 2000 + Number(valeur.slice(0, 2));
  const mois = Number(valeur.slice(2, 4));
  let jour = Number(valeur.slice(4, 6));
  if (mois < 1 || mois > 12) {
    return null;
  }
  if (jour === 0) {
    jour = new Date(Date.UTC(annee, mois, 0)).getUTCDate();
  }
  const p = (n: number) => `${n}`.padStart(2, '0');
  return `${annee}-${p(mois)}-${p(jour)}`;
}
