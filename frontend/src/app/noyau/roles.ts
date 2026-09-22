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

/** Une entree de menu, et qui la voit. */
export interface EntreeDeMenu {
  chemin: string;
  libelle: string;
  icone: string;
  roles: Role[];
  /** Les gestes de tous les jours, ceux qui vont dans la barre du bas sur telephone. */
  principal?: boolean;
}

export const MENU: EntreeDeMenu[] = [
  {
    chemin: '/accueil',
    libelle: 'Accueil',
    icone: 'dashboard',
    // Le caissier et le magasinier en sont ecartes : ce qu'ils y liraient — valeur du magasin,
    // recette du jour — ne les regarde pas, et les conduirait a un clic de plus avant l'ecran ou
    // ils travaillent.
    roles: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_COMPTABLE', 'ROLE_SUPER_ADMIN'],
  },
  {
    chemin: '/comptoir',
    libelle: 'Vendre',
    icone: 'point_of_sale',
    roles: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_CAISSIER'],
    principal: true,
  },
  {
    chemin: '/stock',
    libelle: 'Stock',
    icone: 'inventory_2',
    roles: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER', 'ROLE_COMPTABLE'],
    principal: true,
  },
  {
    chemin: '/articles',
    libelle: 'Catalogue',
    icone: 'category',
    // Une seule entree pour les deux ecrans : on gere les categories depuis le catalogue, parce
    // qu'on en cree cinq une fois pour toutes la ou l'on ajoute des articles toute l'annee.
    roles: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER'],
  },
  {
    chemin: '/receptions',
    libelle: 'Réceptions',
    icone: 'local_shipping',
    roles: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER'],
    principal: true,
  },
  {
    chemin: '/clients',
    libelle: 'Répertoire',
    icone: 'contacts',
    // Une seule entree pour les clients et les fournisseurs : meme ecran, et la bascule
    // n'apparait qu'a qui peut reellement voir les deux.
    roles: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_CAISSIER', 'ROLE_MAGASINIER'],
  },
  {
    chemin: '/factures',
    libelle: 'Factures',
    icone: 'receipt_long',
    roles: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_COMPTABLE'],
  },
  {
    chemin: '/caisse',
    libelle: 'Caisse',
    icone: 'account_balance_wallet',
    roles: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_CAISSIER', 'ROLE_COMPTABLE'],
  },
  {
    chemin: '/comptes',
    libelle: 'Comptes',
    icone: 'group',
    roles: ['ROLE_ADMIN', 'ROLE_SUPER_ADMIN'],
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
