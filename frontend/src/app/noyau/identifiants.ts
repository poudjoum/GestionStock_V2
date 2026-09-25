/**
 * Un identifiant tire par le poste de vente, et qui ne se repete pas.
 *
 * Il sert a rendre l'envoi d'une vente rejouable : reposter la meme reference rend la vente deja
 * enregistree au lieu d'en creer une seconde. Deux ventes qui porteraient le meme identifiant
 * seraient donc confondues — l'une des deux disparaitrait, avec sa sortie de stock.
 *
 * <b>Pourquoi pas `crypto.randomUUID()` seul.</b> Cette fonction n'existe que dans un contexte
 * securise : HTTPS, ou `localhost`. L'application tourne sur le reseau du magasin, en clair, a
 * une adresse du genre `http://192.168.1.100:9093` — le navigateur n'y expose pas `randomUUID`,
 * et l'encaissement echouait sur « crypto.randomUUID is not a function », au moment precis ou le
 * client tendait son argent.
 *
 * `crypto.getRandomValues`, lui, est disponible partout, contexte securise ou non. Ces seize
 * octets aleatoires, arranges selon la version 4 de la norme, valent exactement ce que
 * `randomUUID` aurait rendu.
 */
export function identifiantDeVente(): string {
  if (typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }

  const octets = crypto.getRandomValues(new Uint8Array(16));
  // Le quatrieme bloc dit la version — 4, tirage aleatoire — et le cinquieme la variante. Sans
  // ces deux marques, la chaine ressemble a un UUID sans en etre un, et un serveur qui la
  // validerait la refuserait.
  octets[6] = (octets[6] & 0x0f) | 0x40;
  octets[8] = (octets[8] & 0x3f) | 0x80;

  const hex = Array.from(octets, (octet) => octet.toString(16).padStart(2, '0')).join('');
  return [
    hex.slice(0, 8),
    hex.slice(8, 12),
    hex.slice(12, 16),
    hex.slice(16, 20),
    hex.slice(20),
  ].join('-');
}
