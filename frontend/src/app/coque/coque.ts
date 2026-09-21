import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Session } from '../noyau/session';
import { Notifications } from '../notifications/notifications.service';
import { LIBELLE_DES_ROLES, menuPour } from '../noyau/roles';

/**
 * La coque : ce qui entoure chaque ecran.
 *
 * Le menu vient du role, et non d'une liste figee. Six roles font six metiers differents, et
 * montrer a un caissier les ecrans du comptable ne sert qu'a l'egarer.
 *
 * Deux dispositions, pas une mise en page qui retrecit. Sur telephone, les gestes de tous les
 * jours vont dans une barre en bas, sous le pouce ; sur un ecran large, tout le menu tient dans
 * une colonne a gauche. Le tableau d'inventaire du comptable ecrase sur cinq pouces ne sert
 * personne, et l'ecran de vente du caissier etale sur vingt-sept pouces non plus.
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
    MatToolbarModule,
  ],
  templateUrl: './coque.html',
})
export class Coque implements OnInit {
  private readonly session = inject(Session);
  private readonly notifications = inject(Notifications);

  protected readonly username = this.session.username;
  protected readonly nonLues = this.notifications.nonLues;
  protected readonly entrees = computed(() => menuPour(this.session.roles()));
  protected readonly principales = computed(() => this.entrees().filter((e) => e.principal));
  protected readonly metier = computed(() => {
    const roles = this.session.roles().filter((role) => role !== 'ROLE_USER');
    return roles.map((role) => LIBELLE_DES_ROLES[role]).join(', ');
  });
  protected readonly menuOuvert = signal(false);

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
