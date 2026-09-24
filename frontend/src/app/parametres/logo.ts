/**
 * Preparer un logo pour le rouleau de caisse.
 *
 * L'image choisie par le gerant sort d'un telephone ou d'un ordinateur : elle fait volontiers
 * deux mille pixels de large et plusieurs mega-octets. Le papier, lui, compte 384 points sur sa
 * largeur — c'est la definition d'une tete thermique 80 mm a 203 points par pouce, et pas un de
 * plus ne sera imprime.
 *
 * On reduit donc ici, dans le navigateur, avant d'envoyer quoi que ce soit : ce qui part vers le
 * serveur pese alors quelques kilo-octets, se relit a chaque impression sans peser sur la page,
 * et tient dans une colonne de base de donnees sans qu'on ait a inventer un stockage de fichiers.
 */

/** La largeur de la tete d'impression, en points. Rien au-dela n'atteindra le papier. */
const LARGEUR_MAX = 384;

/**
 * Le seuil a partir duquel on renonce au PNG.
 *
 * Un logo — quelques aplats, du texte — donne un PNG minuscule. Une photographie, non : elle
 * gonfle, et c'est le signe qu'il faut passer a un format qui accepte de perdre quelque chose.
 */
const TROP_LOURD = 120_000;

/** Lit le fichier choisi et rend l'image reduite, encodee, prete a etre enregistree. */
export async function preparerLeLogo(fichier: File): Promise<string> {
  const image = await lire(fichier);
  const echelle = Math.min(1, LARGEUR_MAX / image.width);
  const largeur = Math.max(1, Math.round(image.width * echelle));
  const hauteur = Math.max(1, Math.round(image.height * echelle));

  const toile = document.createElement('canvas');
  toile.width = largeur;
  toile.height = hauteur;
  const pinceau = toile.getContext('2d');
  if (!pinceau) {
    throw new Error("Ce navigateur ne sait pas redimensionner l'image.");
  }
  // Un fond blanc avant de dessiner : une image transparente virerait au noir en JPEG, et le
  // papier est blanc de toute facon.
  pinceau.fillStyle = '#fff';
  pinceau.fillRect(0, 0, largeur, hauteur);
  pinceau.drawImage(image, 0, 0, largeur, hauteur);

  const png = toile.toDataURL('image/png');
  return png.length <= TROP_LOURD ? png : toile.toDataURL('image/jpeg', 0.85);
}

function lire(fichier: File): Promise<HTMLImageElement> {
  return new Promise((resoudre, rejeter) => {
    const lecteur = new FileReader();
    lecteur.onerror = () => rejeter(new Error('Le fichier n’a pas pu être lu.'));
    lecteur.onload = () => {
      const image = new Image();
      image.onload = () => resoudre(image);
      image.onerror = () => rejeter(new Error('Ce fichier n’est pas une image.'));
      image.src = String(lecteur.result);
    };
    lecteur.readAsDataURL(fichier);
  });
}
