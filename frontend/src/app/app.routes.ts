import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { gardeConnecte, gardeDeconnecte, gardeRoles } from './noyau/gardes';
import { accueilPour } from './noyau/roles';
import { Session } from './noyau/session';

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
        path: 'accueil',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_COMPTABLE', 'ROLE_SUPER_ADMIN')],
        loadComponent: () => import('./accueil/tableau-de-bord').then((m) => m.TableauDeBord),
      },
      {
        path: 'notifications',
        loadComponent: () =>
          import('./notifications/liste-notifications').then((m) => m.ListeNotifications),
      },
      {
        path: 'comptoir',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_CAISSIER')],
        loadComponent: () =>
          import('./comptoir/vente-au-comptoir').then((m) => m.VenteAuComptoir),
      },
      {
        path: 'stock',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER', 'ROLE_COMPTABLE')],
        loadComponent: () => import('./stock/etat-du-stock').then((m) => m.EtatDuStock),
      },
      {
        path: 'articles',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER')],
        loadComponent: () => import('./catalogue/articles').then((m) => m.Articles),
      },
      {
        path: 'categories',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER')],
        loadComponent: () => import('./catalogue/categories').then((m) => m.Categories),
      },
      {
        // Sans le magasinier, contrairement aux deux ecrans ci-dessus : un import reecrit les
        // prix de tout le magasin en un geste. Il tient la marchandise, pas la politique de prix.
        path: 'articles/import',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER')],
        loadComponent: () => import('./catalogue/import-articles').then((m) => m.ImportArticles),
      },
      // Les achats : passer une commande, puis recevoir ce qui arrive. Le chemin `/receptions`
      // ne couvrait que le second temps — on ne pouvait pas commander depuis l'application.
      {
        path: 'achats',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER')],
        loadComponent: () => import('./achats/liste-achats').then((m) => m.ListeAchats),
      },
      {
        path: 'achats/nouvelle',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER')],
        loadComponent: () =>
          import('./achats/commande-fournisseur').then((m) => m.CommandeFournisseur),
      },
      {
        path: 'achats/:id',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER')],
        loadComponent: () =>
          import('./achats/commande-fournisseur').then((m) => m.CommandeFournisseur),
      },
      {
        path: 'achats/:id/reception',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER')],
        loadComponent: () => import('./achats/reception').then((m) => m.Reception),
      },
      // Un signet sur l'ancien chemin continue de mener au bon endroit.
      { path: 'receptions', redirectTo: 'achats' },
      {
        path: 'clients',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_CAISSIER')],
        loadComponent: () => import('./repertoire/repertoire').then((m) => m.RepertoireEcran),
      },
      {
        path: 'fournisseurs',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER')],
        loadComponent: () => import('./repertoire/repertoire').then((m) => m.RepertoireEcran),
      },
      {
        path: 'factures',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_COMPTABLE')],
        loadComponent: () => import('./factures/liste-factures').then((m) => m.ListeFactures),
      },
      {
        path: 'caisse',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_CAISSIER', 'ROLE_COMPTABLE')],
        loadComponent: () => import('./caisse/etat-de-caisse').then((m) => m.EtatDeCaisse),
      },
      {
        path: 'comptes',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')],
        loadComponent: () => import('./comptes/comptes').then((m) => m.Comptes),
      },
      {
        // L'identite du magasin : ce que le ticket de caisse imprime en en-tete. Reserve a
        // l'administrateur, seul que le serveur laisse ecrire sur son entreprise.
        path: 'parametres',
        canActivate: [gardeRoles('ROLE_ADMIN')],
        loadComponent: () =>
          import('./parametres/identite-du-magasin').then((m) => m.IdentiteDuMagasin),
      },
      // La racine mene a l'accueil du role, et non a une page fixe.
      //
      // Elle sert a ceux qui arrivent par un signet ou en tapant l'adresse : un administrateur
      // tombait alors sur ses notifications plutot que sur son tableau de bord, et un caissier
      // sur un ecran qui ne le concerne pas. La redirection se resout au moment ou l'on passe,
      // avec les roles qu'on a ce jour-la.
      {
        path: '',
        pathMatch: 'full',
        redirectTo: () => accueilPour(inject(Session).roles()),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
