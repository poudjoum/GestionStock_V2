/**
 * Sortir un ticket sur le rouleau de 80 mm du comptoir.
 *
 * Il n'y a pas de bibliotheque ni de pilote la-dessous : pour le systeme, une thermique est une
 * imprimante comme une autre, et `window.print()` suffit a lui parler.
 */

/**
 * `@page` ne se laisse pas viser par un selecteur : sa taille vaut pour tout le document, et l'on
 * ne peut donc pas la poser dans la feuille de styles sans l'imposer a toutes les impressions de
 * l'application. On l'ajoute juste avant d'imprimer, on la retire juste apres.
 *
 * Hauteur libre : un rouleau n'a pas de format, il se coupe la ou le document s'arrete. Marge
 * nulle parce que la plupart des thermiques 80 mm impriment sur 72 mm et menagent elles-memes
 * leur blanc lateral — en ajouter un second decentre la bande.
 */
const ROULEAU_80 = '@page { size: 80mm auto; margin: 0; }';

/** La classe que la feuille de styles laisse seule visible a l'impression. */
export const SUPPORT = 'support-impression';

/**
 * Imprime ce seul element, sur le rouleau.
 *
 * Une copie de l'element est posee directement sous `body`, et non imprimee la ou elle se trouve.
 * C'est le point delicat : le ticket vit au fond de la coque de l'application, dont plusieurs
 * conteneurs portent `overflow: hidden` et une hauteur fixe. Le masquage classique — tout cacher
 * sauf le document, puis le sortir en position absolue — le fait alors decouper par un ancetre,
 * et il manque la moitie du ticket sans qu'on voie pourquoi a l'ecran.
 *
 * Sous `body`, plus aucun ancetre ne peut le rogner, et la regle d'impression se reduit a deux
 * lignes : cacher les autres enfants de `body`, montrer celui-ci.
 *
 * La copie plutot que l'original : deplacer un noeud qu'Angular tient dans une vue reviendrait a
 * lui retirer le sol sous les pieds. Le logo etant une image encodee dans la page, la copie est
 * complete des sa creation — rien a attendre avant d'imprimer.
 */
export function imprimerLeTicket(contenu: HTMLElement): void {
  imprimer(contenu, ROULEAU_80);
}

/**
 * Un document de bureau sur A4 portrait — le rapport du mois —, avec ses marges.
 *
 * `titre` devient, le temps de l'impression, celui de la page : c'est le nom que le navigateur
 * propose au fichier quand on choisit « Enregistrer en PDF ». Sans lui, tous les rapports
 * s'appelleraient « GestionStock.pdf ».
 */
export function imprimerEnA4(contenu: HTMLElement, titre?: string): void {
  const avant = document.title;
  if (titre) {
    document.title = titre;
  }
  imprimer(contenu, A4, () => (document.title = avant));
}

/** Le format de bureau : A4 portrait, 12 mm de marge. */
const A4 = '@page { size: A4 portrait; margin: 12mm; }';

function imprimer(contenu: HTMLElement, regleDePage: string, apres?: () => void): void {
  const support = document.createElement('div');
  support.className = SUPPORT;
  support.appendChild(contenu.cloneNode(true));
  document.body.appendChild(support);

  const regle = document.createElement('style');
  regle.media = 'print';
  regle.textContent = regleDePage;
  document.head.appendChild(regle);

  let fait = false;
  const nettoyer = () => {
    if (fait) {
      return;
    }
    fait = true;
    support.remove();
    regle.remove();
    window.removeEventListener('afterprint', nettoyer);
    apres?.();
  };
  window.addEventListener('afterprint', nettoyer);

  window.print();

  // Filet : « afterprint » ne part pas sur tous les navigateurs — Safari, notamment, l'ignore
  // encore. Sans ce repli, le support resterait dans la page et la regle sur le document suivant.
  setTimeout(nettoyer, 1000);
}
