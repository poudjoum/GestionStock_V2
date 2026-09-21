import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import { environnement } from '../../environnements/environnement';
import { Session } from '../noyau/session';
import { LIBELLE_DES_ROLES, Role } from '../noyau/roles';
import { messageDErreur } from '../noyau/erreurs';
import type { UserDto } from '../noyau/api';

const API = `${environnement.api}/gestiondestock/v1/users`;

/** Les roles qu'un administrateur distribue. Le super-administrateur ne se donne pas d'ici. */
const ROLES_ATTRIBUABLES: Role[] = [
  'ROLE_ADMIN',
  'ROLE_MANAGER',
  'ROLE_MAGASINIER',
  'ROLE_CAISSIER',
  'ROLE_COMPTABLE',
  'ROLE_USER',
];

/**
 * L'administration des comptes.
 *
 * Fermer plutot que supprimer : l'employe parti reste l'auteur des ventes qu'il a saisies, et
 * effacer son compte rendrait cet historique illisible. C'est pourquoi l'ecran n'offre pas de
 * suppression, seulement un interrupteur.
 *
 * Les gestes sensibles restent au serveur : il refuse qu'un administrateur se ferme lui-meme, et
 * qu'il s'accorde le rang de super-administrateur. L'ecran n'en est qu'un reflet.
 */
@Component({
  selector: 'app-comptes',
  imports: [
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatTooltipModule,
  ],
  templateUrl: './comptes.html',
})
export class Comptes implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly session = inject(Session);
  private readonly snack = inject(MatSnackBar);

  protected readonly comptes = signal<UserDto[]>([]);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);
  protected readonly recherche = signal('');

  protected readonly ouvert = signal<UserDto | null>(null);
  protected readonly rolesChoisis = signal<Role[]>([]);
  protected readonly nouveauMotDePasse = signal('');
  protected readonly envoiEnCours = signal(false);

  protected readonly rolesAttribuables = ROLES_ATTRIBUABLES;
  protected readonly libelles = LIBELLE_DES_ROLES;

  ngOnInit(): void {
    this.charger();
  }

  /** Le filtre est local : la liste des comptes d'une maison tient sur un ecran. */
  protected filtres(): UserDto[] {
    const q = this.recherche().trim().toLowerCase();
    if (!q) {
      return this.comptes();
    }
    return this.comptes().filter((c) =>
      [c.username, c.nom, c.prenoms, c.email].some((champ) => champ?.toLowerCase().includes(q)),
    );
  }

  protected rolesDe(compte: UserDto): Role[] {
    return (compte.roles ?? [])
      .map((r) => r.roleName)
      .filter((nom): nom is Role => !!nom);
  }

  protected libelleDesRoles(compte: UserDto): string {
    const roles = this.rolesDe(compte).filter((r) => r !== 'ROLE_USER');
    return roles.map((r) => LIBELLE_DES_ROLES[r]).join(', ') || 'Aucun rôle';
  }

  protected initiales(compte: UserDto): string {
    return (compte.username ?? '?').slice(0, 2).toUpperCase();
  }

  /** Se fermer soi-meme laisserait une entreprise sans personne pour rouvrir. */
  protected estMoi(compte: UserDto): boolean {
    return compte.username === this.session.username();
  }

  protected ouvrir(compte: UserDto): void {
    this.ouvert.set(compte);
    this.rolesChoisis.set(this.rolesDe(compte));
    this.nouveauMotDePasse.set('');
    this.erreur.set(null);
  }

  protected fermer(): void {
    this.ouvert.set(null);
  }

  protected changerActivation(compte: UserDto, actif: boolean): void {
    this.http.patch<UserDto>(`${API}/${compte.id}/actif/${actif}`, {}).subscribe({
      next: () => {
        this.snack.open(actif ? 'Accès rouvert.' : 'Accès fermé.', 'Fermer', { duration: 3000 });
        this.charger();
      },
      error: (echec: unknown) =>
        this.erreur.set(messageDErreur(echec, "L'accès n'a pas pu être changé.")),
    });
  }

  protected enregistrerLesRoles(): void {
    const compte = this.ouvert();
    if (!compte || this.envoiEnCours()) {
      return;
    }
    this.envoiEnCours.set(true);
    // La liste envoyee est l'etat vise, pas un ajout : c'est ce qui permet de retirer un role
    // sans avoir a le demander separement.
    this.http.patch<UserDto>(`${API}/${compte.id}/roles`, this.rolesChoisis()).subscribe({
      next: () => {
        this.envoiEnCours.set(false);
        this.snack.open('Rôles enregistrés.', 'Fermer', { duration: 3000 });
        this.charger();
        this.fermer();
      },
      error: (echec: unknown) => {
        this.envoiEnCours.set(false);
        this.erreur.set(messageDErreur(echec, "Les rôles n'ont pas pu être changés."));
      },
    });
  }

  protected reinitialiserLeMotDePasse(): void {
    const compte = this.ouvert();
    const nouveau = this.nouveauMotDePasse();
    if (!compte || nouveau.length < 8) {
      return;
    }
    this.envoiEnCours.set(true);
    this.http.patch<UserDto>(`${API}/${compte.id}/motdepasse`, { nouveau }).subscribe({
      next: () => {
        this.envoiEnCours.set(false);
        this.nouveauMotDePasse.set('');
        // Les sessions ouvertes ailleurs tombent avec le mot de passe : le dire evite qu'on
        // s'etonne de voir quelqu'un deconnecte.
        this.snack.open(
          'Mot de passe réinitialisé. Les sessions ouvertes de ce compte sont fermées.',
          'Fermer',
          { duration: 5000 },
        );
      },
      error: (echec: unknown) => {
        this.envoiEnCours.set(false);
        this.erreur.set(messageDErreur(echec, "Le mot de passe n'a pas pu être changé."));
      },
    });
  }

  private charger(): void {
    this.chargement.set(true);
    this.http.get<UserDto[]>(`${API}/all`).subscribe({
      next: (liste) => {
        this.comptes.set(liste);
        this.chargement.set(false);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Les comptes n’ont pas pu être chargés.');
      },
    });
  }
}
