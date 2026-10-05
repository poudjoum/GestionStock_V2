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
    // Hors de la coque : on n'a rien d'autre a faire ici que choisir un mot de passe, et un menu
    // ne ferait que proposer des ecrans ou l'on n'ira pas.
    path: 'premier-mot-de-passe',
    canActivate: [gardeConnecte],
    loadComponent: () =>
      import('./connexion/premier-mot-de-passe').then((m) => m.PremierMotDePasse),
  },
  {
    // Le ticket d'un client, ouvert par le QR : sans compte, hors de la coque. Aucune garde — le
    // client qui scanne n'est pas connecte, et ne doit pas etre renvoye vers une page de connexion.
    path: 't/:code',
    loadComponent: () => import('./ticket/ticket-public').then((m) => m.TicketPublicPage),
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
        data: { cible: 'articles' },
        loadComponent: () => import('./import/import-classeur').then((m) => m.ImportClasseur),
      },
      {
        // L'inventaire : compter le magasin et rattraper les ecarts. Le comptable en est
        // ecarte — il lit les valorisations, il ne compte pas les rayons.
        path: 'inventaire',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER')],
        loadComponent: () => import('./inventaire/inventaire').then((m) => m.InventaireEcran),
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
        path: 'clients/import',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER')],
        data: { cible: 'clients' },
        loadComponent: () => import('./import/import-classeur').then((m) => m.ImportClasseur),
      },
      {
        path: 'fournisseurs/import',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER')],
        data: { cible: 'fournisseurs' },
        loadComponent: () => import('./import/import-classeur').then((m) => m.ImportClasseur),
      },
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
        path: 'sites',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER')],
        loadComponent: () => import('./parametres/sites').then((m) => m.SitesEcran),
      },
      {
        path: 'transferts',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER')],
        loadComponent: () => import('./transferts/transferts').then((m) => m.Transferts),
      },
      {
        path: 'comptes',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_SUPER_ADMIN')],
        loadComponent: () => import('./comptes/comptes').then((m) => m.Comptes),
      },
      {
        // La plateforme : les commerces heberges. L'editeur seul, et le serveur le redit.
        path: 'commerces',
        canActivate: [gardeRoles('ROLE_SUPER_ADMIN')],
        loadComponent: () => import('./plateforme/commerces').then((m) => m.Commerces),
      },
      {
        // L'identite du magasin : ce que le ticket de caisse imprime en en-tete. Reserve a
        // l'administrateur, seul que le serveur laisse ecrire sur son entreprise.
        path: 'parametres',
        canActivate: [gardeRoles('ROLE_ADMIN')],
        loadComponent: () =>
          import('./parametres/identite-du-magasin').then((m) => m.IdentiteDuMagasin),
      },
      {
        // La vitrine du design system : les composants communs dans tous leurs etats. Hors du
        // menu ; on y vient en tapant l'adresse, pour juger un composant ou un changement de jeton.
        path: 'design',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')],
        loadComponent: () => import('./design/vitrine').then((m) => m.Vitrine),
      },
      {
        // Les campagnes de promotion : le gerant met des articles en promotion sur une periode.
        path: 'campagnes',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER')],
        loadComponent: () => import('./campagnes/campagnes').then((m) => m.CampagnesEcran),
      },
      {
        // Le programme de fidelite : une decision commerciale, celle du gerant.
        path: 'fidelite',
        canActivate: [gardeRoles('ROLE_ADMIN', 'ROLE_MANAGER')],
        loadComponent: () =>
          import('./parametres/programme-fidelite').then((m) => m.ProgrammeFidelite),
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
