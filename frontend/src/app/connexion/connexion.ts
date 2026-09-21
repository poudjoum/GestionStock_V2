import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { HttpErrorResponse } from '@angular/common/http';
import { Session } from '../noyau/session';
import { accueilPour } from '../noyau/roles';

@Component({
  selector: 'app-connexion',
  imports: [
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  templateUrl: './connexion.html',
})
export class Connexion {
  private readonly session = inject(Session);
  private readonly router = inject(Router);

  protected readonly username = signal('');
  protected readonly motDePasse = signal('');
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);

  protected soumettre(): void {
    if (this.enCours() || !this.username() || !this.motDePasse()) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);

    this.session.seConnecter(this.username(), this.motDePasse()).subscribe({
      next: () => {
        // Chacun arrive la ou il travaille : le caissier sur l'ecran de vente, le magasinier sur
        // le stock. Un accueil commun obligerait a un clic de plus tous les matins.
        void this.router.navigateByUrl(accueilPour(this.session.roles()));
      },
      error: (echec: unknown) => {
        this.enCours.set(false);
        this.erreur.set(messageDe(echec));
      },
    });
  }
}

function messageDe(echec: unknown): string {
  if (echec instanceof HttpErrorResponse) {
    if (echec.status === 401) {
      // Ne pas distinguer l'identifiant inconnu du mot de passe faux : le dire renseignerait
      // celui qui essaie des identifiants au hasard.
      return 'Identifiant ou mot de passe incorrect.';
    }
    if (echec.status === 0) {
      return "Le serveur ne répond pas. Vérifiez votre connexion.";
    }
  }
  return 'La connexion a échoué. Réessayez.';
}
