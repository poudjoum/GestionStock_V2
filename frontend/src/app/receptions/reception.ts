import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import {
  CommandeFourDto,
  LigneCmndeFournisseurDto,
  Receptions,
} from './receptions.service';
import { messageDErreur } from '../noyau/erreurs';

/** Une ligne de commande, avec ce que le magasinier est en train de saisir. */
interface LigneSaisie {
  ligne: LigneCmndeFournisseurDto;
  recue: number | null;
}

/**
 * La reception de marchandise, telle qu'on la fait au quai.
 *
 * Deux temps : choisir la commande qu'on a en main, puis saisir ce qui est reellement arrive.
 *
 * Ce qu'on saisit est la quantite de **cette arrivee**, jamais le cumul : c'est ce qui a ete
 * compte au dechargement, et demander un cumul obligerait a faire une soustraction de tete devant
 * un camion.
 */
@Component({
  selector: 'app-reception',
  imports: [
    DecimalPipe,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  templateUrl: './reception.html',
})
export class Reception implements OnInit {
  private readonly service = inject(Receptions);
  private readonly snack = inject(MatSnackBar);

  protected readonly recherche = signal('');
  protected readonly commandes = signal<CommandeFourDto[]>([]);
  protected readonly choisie = signal<CommandeFourDto | null>(null);
  protected readonly saisies = signal<LigneSaisie[]>([]);
  protected readonly chargement = signal(true);
  protected readonly envoiEnCours = signal(false);
  protected readonly erreur = signal<string | null>(null);

  /** Rien a envoyer tant qu'aucune quantite n'est saisie : le bouton reste inerte. */
  protected readonly aQuelqueChose = computed(() =>
    this.saisies().some((s) => (s.recue ?? 0) > 0),
  );

  ngOnInit(): void {
    this.charger();
  }

  protected chercher(q: string): void {
    this.recherche.set(q);
    this.charger();
  }

  protected ouvrir(commande: CommandeFourDto): void {
    this.choisie.set(commande);
    this.chargement.set(true);
    this.erreur.set(null);
    this.service.lignes(commande.id!).subscribe({
      next: (lignes) => {
        this.saisies.set(lignes.map((ligne) => ({ ligne, recue: null })));
        this.chargement.set(false);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Les lignes de la commande n’ont pas pu être chargées.');
      },
    });
  }

  protected revenir(): void {
    this.choisie.set(null);
    this.saisies.set([]);
    this.erreur.set(null);
    this.charger();
  }

  protected saisir(index: number, valeur: string): void {
    const nombre = valeur === '' ? null : Number(valeur);
    this.saisies.update((liste) =>
      liste.map((s, i) => (i === index ? { ...s, recue: Number.isFinite(nombre!) ? nombre : null } : s)),
    );
  }

  /** « Tout est arrivé » : remplit chaque ligne avec ce qui reste attendu. */
  protected toutRecu(): void {
    this.saisies.update((liste) =>
      liste.map((s) => ({ ...s, recue: reste(s.ligne) > 0 ? reste(s.ligne) : null })),
    );
  }

  protected reste(ligne: LigneCmndeFournisseurDto): number {
    return reste(ligne);
  }

  /** Recevoir plus que ce qui reste attendu est une erreur de comptage, pas une livraison. */
  protected tropRecu(saisie: LigneSaisie): boolean {
    return (saisie.recue ?? 0) > reste(saisie.ligne);
  }

  protected envoyer(): void {
    const commande = this.choisie();
    if (!commande || this.envoiEnCours() || !this.aQuelqueChose()) {
      return;
    }
    if (this.saisies().some((s) => this.tropRecu(s))) {
      this.erreur.set('Une quantité dépasse ce qui reste attendu.');
      return;
    }

    this.envoiEnCours.set(true);
    this.erreur.set(null);
    const receptions = this.saisies()
      .filter((s) => (s.recue ?? 0) > 0)
      .map((s) => ({ idLigne: s.ligne.id!, quantite: s.recue! }));

    this.service.recevoir(commande.id!, receptions).subscribe({
      next: (apres) => {
        this.envoiEnCours.set(false);
        const solde = apres.etat === 'LIVREE';
        this.snack.open(
          solde ? 'Commande soldée : tout est arrivé.' : 'Réception enregistrée, il reste du.',
          'Fermer',
          { duration: 4000 },
        );
        this.revenir();
      },
      error: (echec: unknown) => {
        this.envoiEnCours.set(false);
        this.erreur.set(messageDErreur(echec, 'La réception n’a pas pu être enregistrée.'));
        if (echec instanceof HttpErrorResponse && echec.status === 400) {
          // Les lignes ont pu bouger entre l'ouverture et l'envoi : on les relit pour que
          // l'ecran cesse d'afficher un reste qui n'est plus vrai.
          this.ouvrir(this.choisie()!);
        }
      },
    });
  }

  private charger(): void {
    this.chargement.set(true);
    this.erreur.set(null);
    this.service.aRecevoir(this.recherche()).subscribe({
      next: (page) => {
        this.commandes.set(page.content ?? []);
        this.chargement.set(false);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Les commandes n’ont pas pu être chargées.');
      },
    });
  }
}

function reste(ligne: LigneCmndeFournisseurDto): number {
  return ligne.resteALivrer ?? 0;
}
