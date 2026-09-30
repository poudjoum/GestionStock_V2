/**
 * Le code imprime sous le QR d'un ticket : douze caracteres de l'alphabet de Crockford.
 *
 * Tire ici, au comptoir, et non par le serveur : un ticket imprime hors ligne doit deja porter le
 * sien, et le serveur le gardera tel quel a la synchronisation. Soixante bits tires au hasard —
 * impossible de deviner le ticket d'un autre — et assez court pour se recopier a la main.
 *
 * Meme regle que `CodeTicket` cote serveur ; les deux doivent rester d'accord sur l'alphabet.
 */
const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';

export function nouveauCodeDeTicket(): string {
  // `getRandomValues`, disponible meme en clair sur le reseau du magasin — contrairement a
  // `randomUUID`. 256 etant un multiple de 32, garder cinq bits par octet ne biaise rien.
  const octets = crypto.getRandomValues(new Uint8Array(12));
  return Array.from(octets, (o) => ALPHABET[o & 31]).join('');
}

/** Le code lisible sur le papier : trois groupes de quatre, plus faciles a recopier. */
export function codeLisible(code: string): string {
  return code.replace(/(.{4})(?=.)/g, '$1-');
}

/** L'adresse que porte le QR. */
export function adresseDuTicket(base: string, code: string): string {
  return `${base.replace(/\/+$/, '')}/t/${code}`;
}
