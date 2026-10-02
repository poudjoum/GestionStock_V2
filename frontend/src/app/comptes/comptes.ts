import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
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
import { EnTetePage, EtatVide, Section, Statut } from '../design';

const API = `${environnement.api}/gestiondestock/v1/users`;

/** Ce que fait chaque metier, en une ligne : on choisit un role en sachant ce qu'il ouvre. */
const CE_QUE_FAIT: Partial<Record<Role, string>> = {
  ROLE_CAISSIER: 'Vend au comptoir, encaisse, gère les clients',
  ROLE_MAGASINIER: 'Catalogue, réceptions, inventaire, fournisseurs',
  ROLE_COMPTABLE: 'Factures, encaissements, état de caisse',
  ROLE_MANAGER: 'Tout le magasin, campagnes et fidélité',
  ROLE_ADMIN: 'Tout, plus le magasin et l’équipe',
};

const ORDRE_DES_ROLES: Role[] = [
  'ROLE_CAISSIER',
  'ROLE_MAGASINIER',
  'ROLE_COMPTABLE',
  'ROLE_MANAGER',
  'ROLE_ADMIN',
];

/** Un collaborateur en cours de saisie. */
interface Saisie {
  prenoms: string;
  nom: string;
  username: string;
  numTel: string;
  email: string;
  motDePasse: string;
  roles: Role[];
}

/**
 * Un mot de passe provisoire qu'on peut dicter : un mot, quatre chiffres. Il ne sert qu'une fois,
 * le temps que le collaborateur en choisisse un a lui.
 */
function motDePasseProvisoire(): string {
  const mots = ['Caisse', 'Rayon', 'Stock', 'Ticket', 'Comptoir', 'Magasin'];
  const tirage = new Uint32Array(2);
  crypto.getRandomValues(tirage);
  return `${mots[tirage[0] % mots.length]}-${String(1000 + (tirage[1] % 9000))}`;
}

/** « Aline Ngo Bassa » donne « aline.ngobassa » : sans accent ni espace, comme un identifiant. */
function identifiantPropose(prenoms: string, nom: string): string {
  const net = (texte: string) =>
    texte
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]/g, '');
  const p = net(prenoms.split(' ')[0] ?? '');
  const n = net(nom);
  return [p, n].filter(Boolean).join('.');
}

function vide(roles: Role[]): Saisie {
  return {
    prenoms: '',
    nom: '',
    username: '',
    numTel: '',
    email: '',
    motDePasse: motDePasseProvisoire(),
    roles: roles.includes('ROLE_CAISSIER') ? ['ROLE_CAISSIER'] : [],
  };
}

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
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatTooltipModule,
    EnTetePage,
    EtatVide,
    Section,
    Statut,
  ],
  templateUrl: './comptes.html',
  styleUrl: './comptes.css',
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

  /** Les roles que l'appelant peut donner, tels que le serveur les accorde. */
  protected readonly rolesAttribuables = signal<Role[]>([]);
  protected readonly libelles = LIBELLE_DES_ROLES;
  protected readonly ceQueFait = CE_QUE_FAIT;

  /** Le volet de droite : la fiche d'un compte, ou l'ajout d'un collaborateur. */
  protected readonly ajout = signal(false);
  protected readonly saisie = signal<Saisie>(vide([]));
  /** Le compte qu'on vient de creer : ses acces s'affichent pour etre dictes. */
  protected readonly cree = signal<{ nom: string; username: string; motDePasse: string } | null>(null);
  /** L'identifiant suit le nom tant qu'on ne l'a pas tape soi-meme. */
  private identifiantTouche = false;

  protected readonly saisieComplete = computed(() => {
    const s = this.saisie();
    return (
      !!s.nom.trim() &&
      !!s.username.trim() &&
      !!s.numTel.trim() &&
      s.motDePasse.length >= 8 &&
      s.roles.length > 0
    );
  });

  ngOnInit(): void {
    this.charger();
    this.http.get<Role[]>(`${API}/roles-attribuables`).subscribe({
      // Du metier de terrain au plus large : c'est l'ordre dans lequel on embauche.
      next: (roles) =>
        this.rolesAttribuables.set(
          ORDRE_DES_ROLES.filter((r) => roles.includes(r)),
        ),
    });
  }

  /**
   * Le gerant ne touche qu'a son equipe : un compte dont tous les roles sont de ceux qu'il donne.
   * Le serveur le refuserait de toute facon ; l'ecran evite de proposer un geste voue a l'echec.
   */
  protected peutModifier(compte: UserDto): boolean {
    const permis = this.rolesAttribuables();
    return this.rolesDe(compte).every((r) => r === 'ROLE_USER' || permis.includes(r));
  }

  protected nouveau(): void {
    this.ouvert.set(null);
    this.cree.set(null);
    this.identifiantTouche = false;
    this.saisie.set(vide(this.rolesAttribuables()));
    this.erreur.set(null);
    this.ajout.set(true);
  }

  protected champ<K extends keyof Saisie>(cle: K, valeur: Saisie[K]): void {
    this.saisie.update((s) => {
      const suite = { ...s, [cle]: valeur };
      if (cle === 'username') {
        this.identifiantTouche = true;
      } else if ((cle === 'nom' || cle === 'prenoms') && !this.identifiantTouche) {
        suite.username = identifiantPropose(suite.prenoms, suite.nom);
      }
      return suite;
    });
  }

  protected basculerRole(role: Role, coche: boolean): void {
    this.saisie.update((s) => ({
      ...s,
      roles: coche ? [...s.roles, role] : s.roles.filter((r) => r !== role),
    }));
  }

  protected nouveauMotDePasseProvisoire(): void {
    this.champ('motDePasse', motDePasseProvisoire());
  }

  protected ajouter(): void {
    const s = this.saisie();
    if (!this.saisieComplete() || this.envoiEnCours()) {
      return;
    }
    this.envoiEnCours.set(true);
    this.erreur.set(null);
    this.http
      .post<UserDto>(`${API}/personnel`, {
        nom: s.nom.trim(),
        prenoms: s.prenoms.trim() || null,
        username: s.username.trim(),
        numTel: s.numTel.trim(),
        email: s.email.trim() || null,
        motDePasse: s.motDePasse,
        roles: s.roles,
      })
      .subscribe({
        next: (compte) => {
          this.envoiEnCours.set(false);
          this.cree.set({
            nom: [s.prenoms.trim(), s.nom.trim()].filter(Boolean).join(' '),
            username: compte.username ?? s.username,
            motDePasse: s.motDePasse,
          });
          this.charger();
        },
        error: (echec: unknown) => {
          this.envoiEnCours.set(false);
          this.erreur.set(messageDErreur(echec, 'Le collaborateur n’a pas pu être ajouté.'));
        },
      });
  }

  protected copierLesAcces(): void {
    const acces = this.cree();
    if (!acces) {
      return;
    }
    const texte =
      `Identifiant : ${acces.username}\nMot de passe provisoire : ${acces.motDePasse}\n` +
      `Adresse : ${location.origin}`;
    void navigator.clipboard?.writeText(texte).then(
      () => this.snack.open('Accès copiés.', 'Fermer', { duration: 2500 }),
      () => undefined,
    );
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
    if (!this.peutModifier(compte)) {
      return;
    }
    this.ajout.set(false);
    this.cree.set(null);
    this.ouvert.set(compte);
    this.rolesChoisis.set(this.rolesDe(compte));
    this.nouveauMotDePasse.set('');
    this.erreur.set(null);
  }

  protected fermer(): void {
    this.ouvert.set(null);
    this.ajout.set(false);
    this.cree.set(null);
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
        // s'etonne de voir quelqu'un deconnecte. Il est provisoire : son titulaire en choisira
        // un autre a la connexion suivante.
        this.snack.open(
          'Mot de passe réinitialisé : à changer à la prochaine connexion. Les sessions ouvertes sont fermées.',
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
