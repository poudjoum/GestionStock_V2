import { Routes } from '@angular/router';
import { gardeConnecte, gardeDeconnecte, gardeRoles } from './noyau/gardes';

/**
 * Les ecrans encore a ecrire sont declares mais vides.
 *
 * Les laisser absents ferait tomber le menu sur des routes inconnues ; les declarer ici dit ce
 * qui vient, et le lot suivant remplace le composant sans toucher au reste.
 */
const aVenir = () => import('./provisoire/a-venir').then((m) => m.AVenir);

export const routes: Routes = [
  {
    path: 'connexion',
    canActivate: [gardeDeconnecte],
    loadComponent: () => import('./connexion/connexion').then((m) => m.Connexion),
  },
  {
    path: '',
    canActivate: [gardeConnecte],
    loadComponent: () => import('./coque/coque').then((m) => m.Coque),
    children: [
      {
        path: 'notifications',
        loadComponent: () =>
          import('./notifications/liste-notifications').then((m) => m.ListeNotifications),
      },
      {
        path: 'comptoir',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_CAISSIER')],
        loadComponent: aVenir,
        data: { titre: 'Vendre au comptoir' },
      },
      {
        path: 'stock',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER', 'ROLE_COMPTABLE')],
        loadComponent: aVenir,
        data: { titre: 'État du stock' },
      },
      {
        path: 'receptions',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER')],
        loadComponent: aVenir,
        data: { titre: 'Réception de marchandise' },
      },
      {
        path: 'factures',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_COMPTABLE')],
        loadComponent: aVenir,
        data: { titre: 'Factures' },
      },
      {
        path: 'caisse',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_CAISSIER', 'ROLE_COMPTABLE')],
        loadComponent: aVenir,
        data: { titre: 'État de caisse' },
      },
      {
        path: 'comptes',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')],
        loadComponent: aVenir,
        data: { titre: 'Comptes' },
      },
      // La racine mene a l'accueil du role : la redirection se fait a la connexion, et cette
      // entree ne sert qu'a ceux qui arrivent par un signet.
      { path: '', pathMatch: 'full', redirectTo: 'notifications' },
    ],
  },
  { path: '**', redirectTo: '' },
];
