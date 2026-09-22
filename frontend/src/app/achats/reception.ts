import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Achats, CommandeFourDto, LigneCmndeFournisseurDto } from './achats.service';
import { messageDErreur } from '../noyau/erreurs';

/** Une ligne de commande, avec ce que le magasinier est en train de saisir. */
interface LigneSaisie {
  ligne: LigneCmndeFournisseurDto;
  recue: number | null;
}

/**
 * La reception de marchandise, telle qu'on la fait au quai.
 *
 * Ce qu'on saisit est la quantite de **cette arrivee**, jamais le cumul : c'est ce qui a ete
 * compte au dechargement, et demander un cumul obligerait a faire une soustraction de tete devant
 * un camion.
 *
 * La commande arrive par son adresse — `/achats/12/reception` — et non par une liste interne :
 * c'est la liste des achats qui choisit, et revenir en arriere y retourne plutot que de remonter
 * d'un etage dans un ecran qui en contenait deux.
 *
 * Quand le reliquat ne viendra jamais, on le clot ici, avec son motif. Rien n'entre alors en
 * stock : clore, c'est constater une absence.
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
  ],
  templateUrl: './reception.html',
})
export class Reception implements OnInit {
  private readonly service = inject(Achats);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly snack = inject(MatSnackBar);

  protected readonly commande = signal<CommandeFourDto | null>(null);
  protected readonly saisies = signal<LigneSaisie[]>([]);
  protected readonly chargement = signal(true);
  protected readonly envoiEnCours = signal(false);
  protected readonly erreur = signal<string | null>(null);

  protected readonly cloture = signal(false);
  protected readonly motif = signal('');

  /** Rien a envoyer tant qu'aucune quantite n'est saisie : le bouton reste inerte. */
  protected readonly aQuelqueChose = computed(() => this.saisies().some((s) => (s.recue ?? 0) > 0));
  protected readonly attendu = computed(() =>
    this.saisies().reduce((somme, s) => somme + reste(s.ligne), 0),
  );
  /** Seule une commande deja entamee a un reliquat a clore. */
  protected readonly cloturable = computed(() => this.commande()?.etat === 'PARTIELLEMENT_LIVREE');

  ngOnInit(): void {
    this.charger(Number(this.route.snapshot.paramMap.get('id')));
  }

  protected retour(): void {
    void this.router.navigate(['/achats'], { queryParams: { vue: 'attendues' } });
  }

  protected saisir(index: number, valeur: string): void {
    const nombre = valeur === '' ? null : Number(valeur);
    this.saisies.update((liste) =>
      liste.map((s, i) =>
        i === index ? { ...s, recue: Number.isFinite(nombre!) ? nombre : null } : s,
      ),
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
    const commande = this.commande();
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
          solde ? 'Commande soldée : tout est arrivé.' : 'Réception enregistrée, il reste dû.',
          'Fermer',
          { duration: 4000 },
        );
        if (solde) {
          this.retour();
        } else {
          // Il reste du : on relit, pour que le reste affiche soit celui d'apres la livraison.
          this.charger(commande.id!);
        }
      },
      error: (echec: unknown) => {
        this.envoiEnCours.set(false);
        this.erreur.set(messageDErreur(echec, 'La réception n’a pas pu être enregistrée.'));
        if (echec instanceof HttpErrorResponse && echec.status === 400) {
          // Les lignes ont pu bouger entre l'ouverture et l'envoi : on les relit pour que
          // l'ecran cesse d'afficher un reste qui n'est plus vrai.
          this.charger(commande.id!);
        }
      },
    });
  }

  protected cloturer(): void {
    const commande = this.commande();
    if (!commande || this.envoiEnCours() || !this.motif().trim()) {
      return;
    }
    this.envoiEnCours.set(true);
    this.erreur.set(null);
    this.service.cloturer(commande.id!, this.motif().trim()).subscribe({
      next: () => {
        this.envoiEnCours.set(false);
        this.snack.open('Reliquat clôturé : on ne l’attend plus.', 'Fermer', { duration: 4000 });
        this.retour();
      },
      error: (echec: unknown) => {
        this.envoiEnCours.set(false);
        this.erreur.set(messageDErreur(echec, 'Le reliquat n’a pas pu être clôturé.'));
      },
    });
  }

  private charger(id: number): void {
    this.chargement.set(true);
    this.erreur.set(null);
    this.cloture.set(false);
    this.motif.set('');

    this.service.detail(id).subscribe({
      next: (commande) => this.commande.set(commande),
      error: (echec: unknown) => {
        this.chargement.set(false);
        this.erreur.set(messageDErreur(echec, 'La commande n’a pas pu être chargée.'));
      },
    });

    this.service.lignes(id).subscribe({
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
}

function reste(ligne: LigneCmndeFournisseurDto): number {
  return Number(ligne.resteALivrer ?? 0);
}
