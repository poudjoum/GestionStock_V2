import { Component, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { environnement } from '../../environnements/environnement';
import { messageDErreur } from '../noyau/erreurs';
import { Session } from '../noyau/session';
import { accueilPour } from '../noyau/roles';
import type { UserDto } from '../noyau/api';

/** Ce que le serveur exige aussi, verifie ici pour ne pas faire l'aller-retour pour rien. */
const LONGUEUR_MINIMALE = 8;

/**
 * Le changement du mot de passe provisoire, a la premiere connexion.
 *
 * Le gerant d'un commerce recoit ses acces par courriel : un identifiant et un mot de passe que
 * l'editeur a choisi, et que le courriel a promene en clair. Tant qu'il n'en a pas choisi un
 * autre, deux personnes connaissent son mot de passe, et une boite aux lettres le conserve.
 *
 * Cet ecran est donc un passage, pas une option : on y arrive des la connexion, et l'on n'en sort
 * que par un mot de passe neuf. Il vit hors de la coque de l'application — ni menu, ni barre, rien
 * a faire d'autre.
 *
 * Le provisoire est redemande : c'est ce qu'exige le changement de mot de passe ordinaire, et le
 * garder de cote depuis l'ecran de connexion pour l'eviter reviendrait a promener un mot de passe
 * dans la memoire de l'application pour economiser une ligne de saisie.
 */
@Component({
  selector: 'app-premier-mot-de-passe',
  imports: [
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  templateUrl: './premier-mot-de-passe.html',
})
export class PremierMotDePasse {
  private readonly http = inject(HttpClient);
  private readonly session = inject(Session);
  private readonly router = inject(Router);

  protected readonly provisoire = signal('');
  protected readonly nouveau = signal('');
  protected readonly confirmation = signal('');
  protected readonly envoi = signal(false);
  protected readonly erreur = signal<string | null>(null);

  protected readonly username = this.session.username;

  protected readonly tropCourt = computed(
    () => this.nouveau().length > 0 && this.nouveau().length < LONGUEUR_MINIMALE,
  );
  protected readonly nonIdentiques = computed(
    () => this.confirmation().length > 0 && this.confirmation() !== this.nouveau(),
  );
  protected readonly complet = computed(
    () =>
      this.provisoire().length > 0 &&
      this.nouveau().length >= LONGUEUR_MINIMALE &&
      this.confirmation() === this.nouveau(),
  );

  protected valider(): void {
    if (this.envoi() || !this.complet()) {
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    this.http
      .patch<UserDto>(`${environnement.api}/gestiondestock/v1/users/moi/motdepasse`, {
        ancien: this.provisoire(),
        nouveau: this.nouveau(),
      })
      .subscribe({
        next: () => {
          // Le serveur a revoque les autres sessions en changeant le mot de passe ; celle-ci
          // tient, puisque c'est elle qui l'a demande. On relit le compte pour que le drapeau
          // tombe aussi de ce cote, puis on va la ou ce role travaille.
          this.session.chargerLeCompte().subscribe({
            next: () => this.entrer(),
            error: () => this.entrer(),
          });
        },
        error: (echec: unknown) => {
          this.envoi.set(false);
          // Le message du serveur, tel quel : « L'ancien mot de passe ne correspond pas » est
          // exactement ce qu'il faut lire quand on a recopie de travers celui du courriel.
          this.erreur.set(messageDErreur(echec, 'Le mot de passe n’a pas pu être changé.'));
        },
      });
  }

  private entrer(): void {
    this.envoi.set(false);
    this.router.navigateByUrl(accueilPour(this.session.roles()));
  }
}
