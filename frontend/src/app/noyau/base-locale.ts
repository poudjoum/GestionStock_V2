/**
 * Ce que l'appareil garde pour lui : la base IndexedDB du navigateur.
 *
 * <b>Pourquoi IndexedDB et non le stockage local.</b> Le stockage local plafonne a cinq mega-octets
 * et bloque la page a chaque ecriture ; un catalogue de quelques milliers d'articles s'en
 * approche, et une vente en file ne doit jamais etre perdue parce que la place manquait.
 * IndexedDB n'a ni l'un ni l'autre defaut, et fonctionne aussi en clair sur le reseau du magasin —
 * contrairement au service worker, qui exige le HTTPS.
 *
 * Trois magasins :
 *   - `catalogue`  les articles, pour vendre sans reseau ;
 *   - `ventes`     les ventes faites hors ligne, en attendant de partir ;
 *   - `reglages`   le reste, par cle : la date du catalogue, l'identite du magasin.
 *
 * Une petite enveloppe a promesses, et pas de bibliotheque : il n'y a que cinq gestes a faire, et
 * une dependance de plus a mettre a jour pour cinq gestes est un mauvais marche.
 */

const NOM = 'gestionstock';
const VERSION = 1;

export type Magasin = 'catalogue' | 'ventes' | 'reglages';

let ouverture: Promise<IDBDatabase> | null = null;

function ouvrir(): Promise<IDBDatabase> {
  ouverture ??= new Promise<IDBDatabase>((resoudre, rejeter) => {
    if (typeof indexedDB === 'undefined') {
      rejeter(new Error('Ce navigateur ne sait pas garder de données hors ligne.'));
      return;
    }
    const demande = indexedDB.open(NOM, VERSION);
    demande.onupgradeneeded = () => {
      const base = demande.result;
      if (!base.objectStoreNames.contains('catalogue')) {
        base.createObjectStore('catalogue', { keyPath: 'id' });
      }
      if (!base.objectStoreNames.contains('ventes')) {
        // La reference tiree par le poste est la cle : c'est deja l'identite de la vente pour le
        // serveur, et elle ne peut donc pas y entrer deux fois.
        base.createObjectStore('ventes', { keyPath: 'referenceClient' });
      }
      if (!base.objectStoreNames.contains('reglages')) {
        base.createObjectStore('reglages');
      }
    };
    demande.onsuccess = () => resoudre(demande.result);
    demande.onerror = () => rejeter(demande.error);
  }).catch((echec: unknown) => {
    // Une ouverture ratee ne doit pas etre gardee : la suivante retentera.
    ouverture = null;
    throw echec;
  });
  return ouverture;
}

/** Une transaction, et la promesse de son aboutissement — pas seulement de sa demande. */
async function transaction<T>(
  magasin: Magasin,
  mode: IDBTransactionMode,
  geste: (store: IDBObjectStore) => IDBRequest<T> | void,
): Promise<T> {
  const base = await ouvrir();
  return new Promise<T>((resoudre, rejeter) => {
    const tx = base.transaction(magasin, mode);
    const demande = geste(tx.objectStore(magasin));
    // On attend la fin de la transaction et non le succes de la demande : une ecriture n'est sur
    // le disque qu'a ce moment-la. Promettre plus tot, c'est annoncer au caissier une vente
    // gardee qui ne l'est pas encore.
    tx.oncomplete = () => resoudre(demande ? demande.result : (undefined as T));
    tx.onerror = () => rejeter(tx.error);
    tx.onabort = () => rejeter(tx.error ?? new Error('Écriture interrompue'));
  });
}

export function toutLire<T>(magasin: Magasin): Promise<T[]> {
  return transaction<T[]>(magasin, 'readonly', (store) => store.getAll() as IDBRequest<T[]>);
}

export function lire<T>(magasin: Magasin, cle: IDBValidKey): Promise<T | undefined> {
  return transaction<T | undefined>(magasin, 'readonly', (store) => store.get(cle) as IDBRequest<T | undefined>);
}

export function ecrire<T>(magasin: Magasin, valeur: T, cle?: IDBValidKey): Promise<void> {
  return transaction<void>(magasin, 'readwrite', (store) => {
    store.put(valeur, cle);
  });
}

export function effacer(magasin: Magasin, cle: IDBValidKey): Promise<void> {
  return transaction<void>(magasin, 'readwrite', (store) => {
    store.delete(cle);
  });
}

/**
 * Remplace tout le contenu d'un magasin, d'un seul geste.
 *
 * Dans une seule transaction : un catalogue a moitie reecrit — coupure au milieu — serait pire
 * que l'ancien, parce qu'il manquerait des articles sans que rien ne le dise.
 */
export function toutRemplacer<T>(magasin: Magasin, valeurs: T[]): Promise<void> {
  return transaction<void>(magasin, 'readwrite', (store) => {
    store.clear();
    for (const valeur of valeurs) {
      store.put(valeur);
    }
  });
}
