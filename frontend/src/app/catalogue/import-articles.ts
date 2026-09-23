import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Catalogue, RapportImport } from './catalogue.service';
import { messageDErreur } from '../noyau/erreurs';

/**
 * Remplir le catalogue depuis un classeur.
 *
 * Ecran d'installation, pas de tous les jours : le gerant s'y assoit une fois, au debut, avec le
 * fichier de son fournisseur ou son ancien inventaire. C'est le moment ou l'abonnement se joue —
 * un commerce qui doit saisir six cents articles a la main n'ouvrira jamais l'application une
 * deuxieme fois.
 *
 * D'ou le parti pris central : **on montre avant d'ecrire**. Le fichier depose est d'abord
 * analyse, le rapport s'affiche, et rien n'entre tant que le gerant n'a pas confirme. Un import
 * qui s'execute puis annonce « 42 erreurs » laisse un catalogue a moitie faux et personne pour
 * dire quelle moitie.
 *
 * La simulation et l'ecriture passent par le meme code cote serveur : ce qui est montre est donc
 * ce qui entrera, et non une estimation.
 */
@Component({
  selector: 'app-import-articles',
  imports: [DecimalPipe, RouterLink, MatButtonModule, MatIconModule, MatProgressBarModule],
  templateUrl: './import-articles.html',
})
export class ImportArticles {
  private readonly catalogue = inject(Catalogue);
  private readonly snack = inject(MatSnackBar);

  protected readonly fichier = signal<File | null>(null);
  protected readonly rapport = signal<RapportImport | null>(null);
  protected readonly analyse = signal(false);
  protected readonly envoi = signal(false);
  protected readonly erreur = signal<string | null>(null);
  /** Survol du depot : sans retour visuel, on ne sait pas si lacher ici fera quelque chose. */
  protected readonly survole = signal(false);

  /** Ce que le gerant vient chercher du regard : combien d'articles vont entrer. */
  protected readonly aEcrire = computed(() => {
    const r = this.rapport();
    return r ? r.creees + r.modifiees : 0;
  });

  protected readonly refusees = computed(() => this.rapport()?.refusees ?? []);

  /**
   * Rien a ecrire : soit le fichier ne portait aucune ligne, soit toutes sont refusees. Dans les
   * deux cas, proposer « Importer » serait proposer un geste sans effet.
   */
  protected readonly rienAEcrire = computed(() => this.rapport() !== null && this.aEcrire() === 0);

  // --- le modele ----------------------------------------------------------------------------

  protected telechargerLeModele(): void {
    this.catalogue.modeleArticles().subscribe({
      next: (classeur) => this.enregistrer(classeur, 'modele-articles.xlsx'),
      error: (echec: unknown) =>
        this.erreur.set(messageDErreur(echec, "Le modèle n'a pas pu être téléchargé.")),
    });
  }

  private enregistrer(contenu: Blob, nom: string): void {
    const url = URL.createObjectURL(contenu);
    const lien = document.createElement('a');
    lien.href = url;
    lien.download = nom;
    lien.click();
    // Sans cette liberation, le classeur reste en memoire jusqu'au rechargement de la page.
    URL.revokeObjectURL(url);
  }

  // --- le fichier ---------------------------------------------------------------------------

  protected choisir(evenement: Event): void {
    const champ = evenement.target as HTMLInputElement;
    const choisi = champ.files?.[0] ?? null;
    // Le champ garde son fichier : sans cette remise a zero, rechoisir le meme fichier apres
    // l'avoir corrige ne declenche aucun evenement, et rien ne se passe.
    champ.value = '';
    this.deposer(choisi);
  }

  protected surSurvol(evenement: DragEvent): void {
    evenement.preventDefault();
    this.survole.set(true);
  }

  protected surSortie(): void {
    this.survole.set(false);
  }

  protected surDepot(evenement: DragEvent): void {
    evenement.preventDefault();
    this.survole.set(false);
    this.deposer(evenement.dataTransfer?.files?.[0] ?? null);
  }

  /** Un fichier choisi est aussitot analyse : demander un second clic pour « voir » n'apporte rien. */
  private deposer(choisi: File | null): void {
    if (!choisi) {
      return;
    }
    this.fichier.set(choisi);
    this.rapport.set(null);
    this.erreur.set(null);
    this.analyse.set(true);

    this.catalogue.importerArticles(choisi, true).subscribe({
      next: (rapport) => {
        this.analyse.set(false);
        this.rapport.set(rapport);
      },
      error: (echec: unknown) => {
        this.analyse.set(false);
        // Le message du serveur dit quelles colonnes il attendait, ou pourquoi le fichier est
        // illisible. Le remplacer par un texte generique effacerait la seule chose utile.
        this.erreur.set(messageDErreur(echec, "Ce fichier n'a pas pu être lu."));
      },
    });
  }

  protected recommencer(): void {
    this.fichier.set(null);
    this.rapport.set(null);
    this.erreur.set(null);
  }

  // --- l'ecriture ---------------------------------------------------------------------------

  protected importer(): void {
    const choisi = this.fichier();
    if (!choisi || this.envoi() || this.rienAEcrire()) {
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);

    this.catalogue.importerArticles(choisi, false).subscribe({
      next: (rapport) => {
        this.envoi.set(false);
        this.rapport.set(rapport);
        this.snack.open(
          `${rapport.creees} article(s) créé(s), ${rapport.modifiees} mis à jour.`,
          'Fermer',
          { duration: 6000 },
        );
      },
      error: (echec: unknown) => {
        this.envoi.set(false);
        this.erreur.set(messageDErreur(echec, "L'import n'a pas pu être effectué."));
      },
    });
  }
}
