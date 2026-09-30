import qrcode from 'qrcode-generator';

/** Un QR pret a dessiner : sa taille en modules, et un chemin SVG qui trace les modules sombres. */
export interface DessinQr {
  modules: number;
  chemin: string;
}

/**
 * Le QR d'un texte, sous forme de chemin SVG.
 *
 * En SVG et non en image : une imprimante thermique rend un vecteur net a toute taille, la ou une
 * image mise a l'echelle bave sur les bords des modules — et un QR aux bords baveux se lit mal.
 *
 * Correction de niveau Q, qui tolere un quart de modules illisibles : un ticket passe dans une poche,
 * se froisse, prend une tache. Le prix — un QR un peu plus dense — est sans importance pour une
 * adresse de quarante caracteres.
 *
 * Le codage lui-meme est confie a `qrcode-generator` : sans dependance, et eprouve depuis des
 * annees. `qr.spec.ts` relit chaque QR produit avec un decodeur independant — « ca ressemble a un
 * QR » ne prouve rien.
 */
export function dessinerQr(texte: string): DessinQr {
  const qr = qrcode(0, 'Q');
  qr.addData(texte, 'Byte');
  qr.make();
  const modules = qr.getModuleCount();
  const traits: string[] = [];
  for (let ligne = 0; ligne < modules; ligne++) {
    for (let colonne = 0; colonne < modules; colonne++) {
      if (qr.isDark(ligne, colonne)) {
        traits.push(`M${colonne} ${ligne}h1v1h-1z`);
      }
    }
  }
  return { modules, chemin: traits.join('') };
}

/** La meme chose en tableau de modules : ce que relit le test. */
export function matriceQr(texte: string): boolean[][] {
  const qr = qrcode(0, 'Q');
  qr.addData(texte, 'Byte');
  qr.make();
  const n = qr.getModuleCount();
  return Array.from({ length: n }, (_, l) => Array.from({ length: n }, (_, c) => qr.isDark(l, c)));
}
