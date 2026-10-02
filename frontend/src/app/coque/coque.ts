import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { filter } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe, DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { Session } from '../noyau/session';
import { Reseau } from '../noyau/reseau';
import { FileDesVentes } from '../comptoir/file-des-ventes';
import { Notifications } from '../notifications/notifications.service';
import { NotificationsPush } from '../notifications/push.service';
import { GROUPES, LIBELLE_DES_ROLES, MENU, accueilPour, groupesPour, menuPour } from '../noyau/roles';
import { Logo } from '../design/logo';

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
    DatePipe,
    DecimalPipe,
    NgTemplateOutlet,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatBadgeModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    Logo,
  ],
  templateUrl: './coque.html',
  styleUrl: './coque.css',
})
export class Coque implements OnInit {
  private readonly session = inject(Session);
  private readonly notifications = inject(Notifications);
  private readonly router = inject(Router);
  private readonly reseau = inject(Reseau);
  private readonly file = inject(FileDesVentes);
  private readonly push = inject(NotificationsPush);

  protected readonly username = this.session.username;
  protected readonly nonLues = this.notifications.nonLues;

  /**
   * L'etat du reseau et des ventes faites hors ligne, visibles de partout.
   *
   * Pas seulement au comptoir : le gerant qui consulte l'etat de caisse doit savoir que des ventes
   * de l'appareil ne sont pas encore arrivees — sans quoi le total qu'il lit est faux, et il ne
   * le sait pas.
   */
  protected readonly joignable = this.reseau.joignable;
  protected readonly aEnvoyer = computed(() => this.file.aEnvoyer().length);
  protected readonly refusees = this.file.refusees;
  protected readonly envoiEnCours = this.file.enCours;
  protected readonly entrees = computed(() => menuPour(this.session.roles()));
  protected readonly principales = computed(() => this.entrees().filter((e) => e.principal));
  protected readonly groupes = computed(() => groupesPour(this.session.roles()));
  /** La page d'arrivee du role : le logo y ramene. */
  protected readonly accueil = computed(() => accueilPour(this.session.roles()));
  /** Le menu complet du telephone, ouvert par le bouton « Menu » de la barre du bas. */
  protected readonly feuilleOuverte = signal(false);
  protected readonly metier = computed(() => {
    const roles = this.session.roles().filter((role) => role !== 'ROLE_USER');
    return roles.map((role) => LIBELLE_DES_ROLES[role]).join(', ') || 'Utilisateur';
  });

  /** Deux lettres dans une pastille : une photo de profil serait un champ de plus a remplir. */
  protected readonly initiales = computed(() =>
    (this.session.username() ?? '?').slice(0, 2).toUpperCase(),
  );

  /** Le groupe de la page courante, au-dessus du titre : « Ventes », « Stock »… */
  protected readonly groupeCourant = signal<string | null>(null);

  /** Le titre de la page courante, lu dans le menu plutot que redeclare par chaque ecran. */
  protected readonly titre = signal('GestionStock');

  constructor() {
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((e) => {
        this.titre.set(titrePour(e.urlAfterRedirects));
        this.groupeCourant.set(groupePour(e.urlAfterRedirects));
        // Une page choisie dans le menu complet du telephone le referme.
        this.feuilleOuverte.set(false);
      });
    this.titre.set(titrePour(this.router.url));
    this.groupeCourant.set(groupePour(this.router.url));
  }

  ngOnInit(): void {
    // Qui suis-je, redemande au serveur : le stockage local sert a dessiner le menu tout de
    // suite, la reponse du serveur le corrige. Un role retire pendant la nuit disparait donc au
    // premier chargement du matin.
    this.session.chargerLeCompte().subscribe({
      next: (compte) => {
        // Le mot de passe est encore celui que l'editeur a envoye par courriel : deux personnes
        // le connaissent, et une boite aux lettres le conserve. On ne va nulle part avant d'en
        // avoir choisi un autre.
        //
        // La redirection est ici, et non dans une garde de route : le compte n'est connu qu'apres
        // cet appel, et une garde qui s'executerait avant lui laisserait passer la premiere
        // navigation — celle qui suit la connexion, justement.
        if (compte.motdepasseAChanger) {
          this.router.navigateByUrl('/premier-mot-de-passe');
        }
      },
      error: () => undefined,
    });
    this.notifications.rafraichirLeCompte();
    // L'abonnement de l'appareil redonne au compte qui vient de se connecter.
    void this.push.demarrer();
  }

  protected envoyerMaintenant(): void {
    void this.file.envoyer();
  }

  protected reessayer(referenceClient: string): void {
    void this.file.reessayer(referenceClient);
  }

  /**
   * L'appareil est retire du compte avant la deconnexion, tant que le jeton vaut encore : sur une
   * caisse partagee, celui qui part ne doit plus y recevoir ses alertes de stock.
   */
  protected async seDeconnecter(): Promise<void> {
    await this.push.oublierCetAppareil();
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
  // La vitrine du design system, hors menu.
  if (chemin === '/design') return 'Design system';
  return 'GestionStock';
}

/** Le nom du groupe de la page, pour la ligne au-dessus du titre. Rien pour l'accueil. */
function groupePour(url: string): string | null {
  const chemin = '/' + (url.split('?')[0].split('/')[1] ?? '');
  const entree = MENU.find((e) => e.chemin === chemin);
  const groupe = GROUPES.find((g) => g.id === entree?.groupe)?.libelle ?? null;
  // « Stock › Stock » ne dirait rien : le groupe ne se repete pas quand il porte le nom de la page.
  return groupe && groupe !== entree?.libelle ? groupe : null;
}
