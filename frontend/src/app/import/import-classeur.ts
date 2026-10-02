import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpClient, HttpContext } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import { RapportImport } from '../catalogue/catalogue.service';
import { DELAI } from '../noyau/intercepteur-delai';
import { messageDErreur } from '../noyau/erreurs';
import { EnTetePage, Section, Statut } from '../design';

const API = `${environnement.api}/gestiondestock/v1`;

/** Ce qu'on importe. Le meme ecran pour les trois : seuls changent les mots et l'adresse. */
export type CibleImport = 'articles' | 'clients' | 'fournisseurs';

interface Cible {
  titre: string;
  intro: string;
  colonnes: string;
  /** Le nom d'une ligne, au singulier et au pluriel : « 3 clients ». */
  unite: [string, string];
  /** L'en-tete de la colonne qui identifie une ligne refusee. */
  repere: string;
  chemin: string;
  retour: { lien: string; libelle: string };
}

const CIBLES: Record<CibleImport, Cible> = {
  articles: {
    titre: 'Importer des articles',
    intro:
      'Remplissez le modèle avec vos articles, déposez-le ici, et vérifiez ce qui va entrer avant de valider. Un article déjà présent est reconnu par son code : le réimporter le met à jour au lieu de le créer deux fois.',
    colonnes:
      'code, désignation, prix HT, taux de TVA, seuil d’alerte, catégorie. Le code est celui que lira la douchette — c’est le code-barres s’il y en a un.',
    unite: ['article', 'articles'],
    repere: 'Code',
    chemin: 'articles/import',
    retour: { lien: '/articles', libelle: 'Voir le catalogue' },
  },
  clients: {
    titre: 'Importer des clients',
    intro:
      'Votre cahier de clients, ou le fichier de votre ancien logiciel, en une fois. Un client déjà présent est reconnu par son téléphone : le réimporter met sa fiche à jour au lieu d’en créer une seconde.',
    colonnes:
      'nom, prénom, téléphone, courriel, adresse, ville, pays. Laissez le prénom vide pour une entreprise — la colonne nom porte alors sa raison sociale.',
    unite: ['client', 'clients'],
    repere: 'Nom',
    chemin: 'clients/import',
    retour: { lien: '/clients', libelle: 'Voir les clients' },
  },
  fournisseurs: {
    titre: 'Importer des fournisseurs',
    intro:
      'Tous vos fournisseurs en une fois. Un fournisseur déjà présent est reconnu par son téléphone : le réimporter met sa fiche à jour au lieu d’en créer une seconde.',
    colonnes:
      'nom, prénom, téléphone, courriel, adresse, ville, pays. Laissez le prénom vide pour une entreprise — la colonne nom porte alors sa raison sociale.',
    unite: ['fournisseur', 'fournisseurs'],
    repere: 'Nom',
    chemin: 'fournisseur/import',
    retour: { lien: '/fournisseurs', libelle: 'Voir les fournisseurs' },
  },
};

/**
 * Remplir un magasin depuis un classeur : le catalogue, les clients ou les fournisseurs.
 *
 * Ecran d'installation, pas de tous les jours : le gerant s'y assoit une fois, au debut, avec le
 * fichier de son fournisseur ou son ancien cahier. C'est le moment ou l'abonnement se joue — un
 * commerce qui doit saisir six cents articles ou deux cents clients a la main n'ouvrira jamais
 * l'application une deuxieme fois.
 *
 * D'ou le parti pris central : **on montre avant d'ecrire**. Le fichier depose est d'abord
 * analyse, le rapport s'affiche, et rien n'entre tant que le gerant n'a pas confirme. La
 * simulation et l'ecriture passent par le meme code cote serveur : ce qui est montre est donc ce
 * qui entrera, et non une estimation.
 */
@Component({
  selector: 'app-import-classeur',
  imports: [DecimalPipe, RouterLink, MatButtonModule, MatIconModule, MatProgressBarModule, EnTetePage, Section, Statut],
  templateUrl: './import-classeur.html',
  styleUrl: './import-classeur.css',
})
export class ImportClasseur {
  private readonly http = inject(HttpClient);
  private readonly snack = inject(MatSnackBar);

  protected readonly cible: Cible = CIBLES[(inject(ActivatedRoute).snapshot.data['cible'] as CibleImport) ?? 'articles'];

  protected readonly fichier = signal<File | null>(null);
  protected readonly rapport = signal<RapportImport | null>(null);
  protected readonly analyse = signal(false);
  protected readonly envoi = signal(false);
  protected readonly erreur = signal<string | null>(null);
  /** Survol du depot : sans retour visuel, on ne sait pas si lacher ici fera quelque chose. */
  protected readonly survole = signal(false);

  protected readonly aEcrire = computed(() => {
    const r = this.rapport();
    return r ? r.creees + r.modifiees : 0;
  });

  protected readonly refusees = computed(() => this.rapport()?.refusees ?? []);

  /** Rien a ecrire : proposer « Importer » serait proposer un geste sans effet. */
  protected readonly rienAEcrire = computed(() => this.rapport() !== null && this.aEcrire() === 0);

  protected unite(n: number): string {
    return this.cible.unite[n > 1 ? 1 : 0];
  }

  // --- le modele ----------------------------------------------------------------------------

  protected telechargerLeModele(): void {
    this.http.get(`${API}/${this.cible.chemin}/modele`, { responseType: 'blob' }).subscribe({
      next: (classeur) => this.enregistrer(classeur, `modele-${this.cible.unite[1]}.xlsx`),
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

    this.envoyer(choisi, true).subscribe({
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

    this.envoyer(choisi, false).subscribe({
      next: (rapport) => {
        this.envoi.set(false);
        this.rapport.set(rapport);
        this.snack.open(`${rapport.creees} créé(s), ${rapport.modifiees} mis à jour.`, 'Fermer', {
          duration: 6000,
        });
      },
      error: (echec: unknown) => {
        this.envoi.set(false);
        this.erreur.set(messageDErreur(echec, "L'import n'a pas pu être effectué."));
      },
    });
  }

  private envoyer(fichier: File, simulation: boolean): Observable<RapportImport> {
    const corps = new FormData();
    corps.append('fichier', fichier);
    return this.http.post<RapportImport>(`${API}/${this.cible.chemin}`, corps, {
      params: { simulation },
      // Dix mille lignes se lisent et s'ecrivent en bien plus de vingt secondes : cet appel pose
      // son propre delai plutot que de faire remonter celui de toute l'application.
      context: new HttpContext().set(DELAI, 180_000),
    });
  }
}
