import type { RoleDto } from './api';

/** Les roles, tels que l'API les nomme. Le type vient de la specification, pas d'une recopie. */
export type Role = NonNullable<RoleDto['roleName']>;

/**
 * Ce que chaque role fait, et ou il le fait.
 *
 * Six roles font six metiers differents, et chacun a deja son terrain : le caissier est debout au
 * comptoir, le magasinier dans les rayons, le comptable assis a son bureau. Ce tableau decide de
 * l'ecran d'accueil et du menu — le reste de l'application n'a pas a s'en soucier.
 *
 * Il ne remplace rien cote serveur : l'API refuse ce qu'elle doit refuser, quel que soit ce que
 * le menu affiche. Ce qui est decrit ici n'est qu'une commodite de navigation.
 */
export const LIBELLE_DES_ROLES: Record<Role, string> = {
  ROLE_SUPER_ADMIN: 'Super-administrateur',
  ROLE_ADMIN: 'Administrateur',
  ROLE_MANAGER: 'Gérant',
  ROLE_MAGASINIER: 'Magasinier',
  ROLE_CAISSIER: 'Caissier',
  ROLE_COMPTABLE: 'Comptable',
  ROLE_USER: 'Utilisateur',
};

/**
 * Les groupes du menu, dans l'ordre ou ils s'affichent.
 *
 * Quinze entrees a plat ne disaient pas ce qui va ensemble. Elles sont rangees comme le metier
 * les pense : ce qu'on vend, ce qu'on a en rayon, ce qu'on commande, ses clients, et les reglages
 * du commerce. `null` : l'entree se place au-dessus des groupes (l'accueil).
 */
export type GroupeDeMenu = 'plateforme' | 'ventes' | 'stock' | 'achats' | 'clients' | 'reglages';

export const GROUPES: { id: GroupeDeMenu; libelle: string }[] = [
  { id: 'plateforme', libelle: 'Plateforme' },
  { id: 'ventes', libelle: 'Ventes' },
  { id: 'stock', libelle: 'Stock' },
  { id: 'achats', libelle: 'Achats' },
  { id: 'clients', libelle: 'Clients et promos' },
  { id: 'reglages', libelle: 'Réglages' },
];

/** Une entree de menu, et qui la voit. */
export interface EntreeDeMenu {
  chemin: string;
  libelle: string;
  icone: string;
  roles: Role[];
  groupe: GroupeDeMenu | null;
  /** Les gestes de tous les jours, ceux qui vont dans la barre du bas sur telephone. */
  principal?: boolean;
}

export const MENU: EntreeDeMenu[] = [
  {
    chemin: '/commerces',
    groupe: 'plateforme',
    libelle: 'Commerces',
    icone: 'storefront',
    // L'editeur seul, et c'est son ecran d'arrivee : il est le premier de la liste, donc celui
    // que `accueilPour` lui rend. Un super-administrateur n'a pas de magasin a lui.
    roles: ['ROLE_SUPER_ADMIN'],
    principal: true,
  },
  {
    chemin: '/accueil',
    groupe: null,
    libelle: 'Accueil',
    icone: 'dashboard',
    // Le caissier et le magasinier en sont ecartes : ce qu'ils y liraient — valeur du magasin,
    // recette du jour — ne les regarde pas, et les conduirait a un clic de plus avant l'ecran ou
    // ils travaillent.
    roles: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_COMPTABLE'],
  },
  {
    chemin: '/comptoir',
    groupe: 'ventes',
    libelle: 'Vendre',
    icone: 'point_of_sale',
    roles: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_CAISSIER'],
    principal: true,
  },
  {
    chemin: '/stock',
    groupe: 'stock',
    libelle: 'Stock',
    icone: 'inventory_2',
    roles: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER', 'ROLE_COMPTABLE'],
    principal: true,
  },
  {
    chemin: '/articles',
    groupe: 'stock',
    libelle: 'Catalogue',
    icone: 'category',
    // Une seule entree pour les deux ecrans : on gere les categories depuis le catalogue, parce
    // qu'on en cree cinq une fois pour toutes la ou l'on ajoute des articles toute l'annee.
    roles: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER'],
  },
  {
    chemin: '/inventaire',
    groupe: 'stock',
    libelle: 'Inventaire',
    icone: 'fact_check',
    // Le magasinier compte, le gerant valide : les deux voient l'ecran, et c'est le serveur qui
    // refuse la validation a qui n'y a pas droit.
    roles: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER'],
  },
  {
    chemin: '/transferts',
    groupe: 'stock',
    libelle: 'Transferts',
    icone: 'swap_horiz',
    // Ceux qui chargent et dechargent : le magasinier du depot expedie, celui du magasin recoit.
    roles: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER'],
  },
  {
    chemin: '/achats',
    groupe: 'achats',
    // Sous le groupe « Achats », l'entree s'appelle « Commandes » : « Achats > Achats » ne
    // dirait rien. Sur la barre du bas du telephone, ou il n'y a pas de groupe, c'est le meme mot.
    // « Achats » et non « Réceptions » : l'entree couvre les deux temps d'une commande
    // fournisseur — la passer, puis recevoir ce qui arrive. Nommer l'ecran d'apres son second
    // temps laissait croire qu'on ne pouvait pas commander.
    libelle: 'Commandes',
    icone: 'local_shipping',
    roles: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER'],
    principal: true,
  },
  {
    chemin: '/clients',
    groupe: 'clients',
    libelle: 'Clients',
    icone: 'contacts',
    // Le repertoire, cote clients. Le caissier en cree au comptoir ; le magasinier n'en a pas
    // l'usage, il a les fournisseurs.
    roles: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_CAISSIER'],
  },
  {
    chemin: '/fournisseurs',
    groupe: 'achats',
    libelle: 'Fournisseurs',
    icone: 'local_shipping',
    // Le meme ecran que les clients, sur l'autre onglet : il lit lequel dans l'adresse.
    roles: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER'],
  },
  {
    chemin: '/factures',
    groupe: 'ventes',
    libelle: 'Factures',
    icone: 'receipt_long',
    roles: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_COMPTABLE'],
  },
  {
    chemin: '/caisse',
    groupe: 'ventes',
    libelle: 'Caisse',
    icone: 'account_balance_wallet',
    roles: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_CAISSIER', 'ROLE_COMPTABLE'],
  },
  {
    chemin: '/campagnes',
    groupe: 'clients',
    libelle: 'Campagnes',
    icone: 'campaign',
    // Les promotions du magasin, qui s'appliquent en caisse et s'affichent dans l'application des
    // clients : comme la politique de points, une decision du gerant.
    roles: ['ROLE_ADMIN', 'ROLE_MANAGER'],
  },
  {
    chemin: '/fidelite',
    groupe: 'clients',
    libelle: 'Fidélité',
    icone: 'loyalty',
    // Le gerant regle ce que rapportent les achats : c'est une decision commerciale, comme un prix.
    roles: ['ROLE_ADMIN', 'ROLE_MANAGER'],
  },
  {
    chemin: '/comptes',
    groupe: 'reglages',
    libelle: 'Équipe',
    icone: 'group',
    // Le gerant y ajoute ses caissiers et ses magasiniers : c'est lui qui embauche au quotidien.
    roles: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_SUPER_ADMIN'],
  },
  {
    chemin: '/sites',
    groupe: 'reglages',
    libelle: 'Sites',
    icone: 'store',
    // Ouvrir un magasin, un entrepot : une decision de gerant, que le serveur reserve aux memes.
    roles: ['ROLE_ADMIN', 'ROLE_MANAGER'],
  },
  {
    chemin: '/parametres',
    groupe: 'reglages',
    libelle: 'Le magasin',
    icone: 'storefront',
    // L'administrateur seul : c'est lui que le serveur laisse ecrire sur son entreprise. Le
    // super-administrateur n'en a aucune — l'ecran n'aurait rien a lui montrer.
    roles: ['ROLE_ADMIN'],
  },
];

/**
 * Ou l'on arrive en se connectant : la premiere entree que le role voit.
 *
 * Le caissier tombe sur l'ecran de vente, le magasinier sur le stock. Un tableau de bord commun
 * obligerait chacun a un clic de plus, tous les matins, pour aller la ou il va de toute facon.
 */
export function accueilPour(roles: Role[]): string {
  const premiere = MENU.find((entree) => entree.roles.some((role) => roles.includes(role)));
  return premiere ? premiere.chemin : '/notifications';
}

export function menuPour(roles: Role[]): EntreeDeMenu[] {
  return MENU.filter((entree) => entree.roles.some((role) => roles.includes(role)));
}

/** Un groupe tel qu'on l'affiche : son nom, et les entrees que ce role y voit. */
export interface GroupeAffiche {
  id: GroupeDeMenu | null;
  libelle: string | null;
  entrees: EntreeDeMenu[];
}

/**
 * Le menu d'un role, range par groupe, sans les groupes vides.
 *
 * Le caissier ne voit que « Ventes » et ses clients ; un groupe dont il ne peut ouvrir aucune
 * entree n'a pas a s'afficher. Les entrees sans groupe passent en tete.
 */
export function groupesPour(roles: Role[]): GroupeAffiche[] {
  const visibles = menuPour(roles);
  const tete = visibles.filter((e) => e.groupe === null);
  const groupes = GROUPES.map((g) => ({
    id: g.id,
    libelle: g.libelle,
    entrees: visibles.filter((e) => e.groupe === g.id),
  })).filter((g) => g.entrees.length > 0);
  return tete.length ? [{ id: null, libelle: null, entrees: tete }, ...groupes] : groupes;
}
