import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { filter } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Session } from '../noyau/session';
import { Notifications } from '../notifications/notifications.service';
import { LIBELLE_DES_ROLES, MENU, menuPour } from '../noyau/roles';

/**
 * La coque : ce qui entoure chaque ecran.
 *
 * Le menu vient du role, et non d'une liste figee. Six roles font six metiers differents, et
 * montrer a un caissier les ecrans du comptable ne sert qu'a l'egarer.
 *
 * Deux dispositions, pas une mise en page qui retrecit. Sur telephone, les gestes de tous les
 * jours vont dans une barre en bas, sous le pouce ; sur un ecran large, tout le menu tient dans
 * une colonne a gauche avec le compte en pied.
 *
 * Seul le contenu defile. La barre du haut et celle du bas restent en place : sur un long
 * inventaire, perdre le titre et la navigation en faisant defiler oblige a remonter pour savoir
 * ou l'on est.
 */
@Component({
  selector: 'app-coque',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatBadgeModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
  ],
  templateUrl: './coque.html',
})
export class Coque implements OnInit {
  private readonly session = inject(Session);
  private readonly notifications = inject(Notifications);
  private readonly router = inject(Router);

  protected readonly username = this.session.username;
  protected readonly nonLues = this.notifications.nonLues;
  protected readonly entrees = computed(() => menuPour(this.session.roles()));
  protected readonly principales = computed(() => this.entrees().filter((e) => e.principal));
  protected readonly metier = computed(() => {
    const roles = this.session.roles().filter((role) => role !== 'ROLE_USER');
    return roles.map((role) => LIBELLE_DES_ROLES[role]).join(', ') || 'Utilisateur';
  });

  /** Deux lettres dans une pastille : une photo de profil serait un champ de plus a remplir. */
  protected readonly initiales = computed(() =>
    (this.session.username() ?? '?').slice(0, 2).toUpperCase(),
  );

  /** Le titre de la page courante, lu dans le menu plutot que redeclare par chaque ecran. */
  protected readonly titre = signal('Gestion de stock');

  constructor() {
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((e) => this.titre.set(titrePour(e.urlAfterRedirects)));
    this.titre.set(titrePour(this.router.url));
  }

  ngOnInit(): void {
    // Qui suis-je, redemande au serveur : le stockage local sert a dessiner le menu tout de
    // suite, la reponse du serveur le corrige. Un role retire pendant la nuit disparait donc au
    // premier chargement du matin.
    this.session.chargerLeCompte().subscribe({ error: () => undefined });
    this.notifications.rafraichirLeCompte();
  }

  protected seDeconnecter(): void {
    this.session.seDeconnecter();
    location.assign('/connexion');
  }
}

function titrePour(url: string): string {
  const chemin = '/' + (url.split('?')[0].split('/')[1] ?? '');
  const entree = MENU.find((e) => e.chemin === chemin);
  if (entree) {
    return entree.libelle;
  }
  if (chemin === '/notifications') return 'Notifications';
  // Les categories n'ont pas d'entree de menu : on y arrive depuis le catalogue.
  if (chemin === '/categories') return 'Catégories';
  if (chemin === '/accueil' || chemin === '/') return 'Accueil';
  return 'Gestion de stock';
}
