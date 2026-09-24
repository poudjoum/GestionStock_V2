import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { messageDErreur } from '../noyau/erreurs';
import {
  CommerceDto,
  InscriptionDto,
  Plateforme,
  ResumePlateformeDto,
  StatutAbonnement,
} from './plateforme.service';

/** Ce que chaque statut dit, et de quelle couleur. Une seule table, lue par tout l'écran. */
const STATUTS: Record<StatutAbonnement, { libelle: string; fond: string; texte: string }> = {
  ACTIF: {
    libelle: 'Actif',
    fond: 'var(--mat-sys-primary-container)',
    texte: 'var(--mat-sys-on-primary-container)',
  },
  ECHU: {
    libelle: 'Échu',
    fond: 'var(--mat-sys-error-container)',
    texte: 'var(--mat-sys-on-error-container)',
  },
  SUSPENDU: {
    libelle: 'Suspendu',
    fond: 'var(--mat-sys-surface-container-highest)',
    texte: 'var(--mat-sys-on-surface-variant)',
  },
};

/** Un commerce vide, prêt à être rempli par le formulaire d'inscription. */
function brouillonVierge(): InscriptionDto {
  return {
    // La TVA est celle du Cameroun par défaut ; le gérant la corrigera dans « Le magasin » s'il
    // n'y est pas assujetti.
    entreprise: { assujettieTva: true, tauxTva: 19.25, adresse: { pays: 'Cameroun' } },
    administrateur: {},
  };
}

/**
 * La plateforme : les commerces hébergés, leur abonnement, leur activité.
 *
 * <b>Qui, et où.</b> L'éditeur, assis à son bureau, devant un grand écran. C'est ce qui décide de
 * la mise en page : un tableau dense plutôt que des cartes, parce qu'on vient comparer des lignes
 * entre elles — quel abonnement arrive à terme, quel client ne se sert plus de l'outil.
 *
 * <b>Ce qu'on vient voir en trois secondes.</b> Combien de commerces, et combien demandent une
 * action. Les échus et ceux qui arrivent à échéance sont donc les deux nombres qui portent une
 * couleur ; les autres restent gris.
 *
 * <b>Le geste.</b> Inscrire un commerce. Il a son bouton, en haut à droite, et il est le seul de
 * cet écran à être plein — tout le reste se consulte.
 */
@Component({
  selector: 'app-commerces',
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  templateUrl: './commerces.html',
})
export class Commerces implements OnInit {
  private readonly service = inject(Plateforme);
  private readonly snack = inject(MatSnackBar);

  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);
  protected readonly commerces = signal<CommerceDto[]>([]);
  protected readonly resume = signal<ResumePlateformeDto | null>(null);
  protected readonly enCours = signal<number | null>(null);

  // L'inscription
  protected readonly formulaireOuvert = signal(false);
  protected readonly brouillon = signal<InscriptionDto>(brouillonVierge());
  protected readonly inscriptionEnCours = signal(false);
  protected readonly erreurInscription = signal<string | null>(null);

  protected readonly statuts = STATUTS;

  /** Ceux qui demandent quelque chose, en tête : un écran de suivi montre d'abord ce qui cloche. */
  protected readonly aTraiter = computed(() =>
    this.commerces().filter((c) => c.statut !== 'ACTIF' || (c.joursRestants ?? 999) <= 30),
  );

  ngOnInit(): void {
    this.charger();
  }

  private charger(): void {
    this.chargement.set(true);
    this.service.commerces().subscribe({
      next: (commerces) => {
        this.commerces.set(commerces);
        this.chargement.set(false);
      },
      error: (echec: unknown) => {
        this.erreur.set(messageDErreur(echec, 'La plateforme n’a pas pu être lue.'));
        this.chargement.set(false);
      },
    });
    this.service.resume().subscribe({
      next: (resume) => this.resume.set(resume),
      error: () => this.resume.set(null),
    });
  }

  protected libelleDuStatut(statut: StatutAbonnement | undefined): string {
    return statut ? STATUTS[statut].libelle : '—';
  }

  /**
   * Ce que dit l'échéance, en clair.
   *
   * « Dans 12 jours » plutôt qu'une date seule : c'est le délai qui décide d'une relance, et
   * compter les jours de tête à chaque ligne est exactement ce qu'un écran doit épargner.
   */
  protected echeanceLisible(commerce: CommerceDto): string {
    const jours = commerce.joursRestants;
    if (jours === null || jours === undefined) {
      return 'Sans abonnement';
    }
    if (jours < 0) {
      return `Échu depuis ${-jours} jour${-jours > 1 ? 's' : ''}`;
    }
    if (jours === 0) {
      return 'Échoit aujourd’hui';
    }
    return `Dans ${jours} jour${jours > 1 ? 's' : ''}`;
  }

  protected renouveler(commerce: CommerceDto): void {
    this.agir(commerce, this.service.renouveler(commerce.id!), (rendu) =>
      `${rendu.nom} est renouvelé jusqu’au ${rendu.abonnementEcheance}.`,
    );
  }

  protected suspendre(commerce: CommerceDto): void {
    this.agir(commerce, this.service.suspendre(commerce.id!), (rendu) =>
      `${rendu.nom} est suspendu : ses comptes n’entrent plus.`,
    );
  }

  protected reprendre(commerce: CommerceDto): void {
    this.agir(commerce, this.service.reprendre(commerce.id!), (rendu) =>
      `${rendu.nom} est rouvert.`,
    );
  }

  /**
   * Le tronc commun des trois actions : marquer la ligne, remplacer ce que le serveur rend, dire
   * ce qui vient de se passer.
   *
   * Le serveur rend la ligne entière plutôt qu'un accusé de réception : les compteurs et le statut
   * se recalculent chez lui, et l'écran n'a pas à deviner ce qu'ils sont devenus.
   */
  private agir(
    commerce: CommerceDto,
    appel: ReturnType<Plateforme['suspendre']>,
    message: (rendu: CommerceDto) => string,
  ): void {
    if (this.enCours() !== null || !commerce.id) {
      return;
    }
    this.enCours.set(commerce.id);
    this.erreur.set(null);
    appel.subscribe({
      next: (rendu) => {
        this.enCours.set(null);
        this.commerces.update((liste) => liste.map((c) => (c.id === rendu.id ? rendu : c)));
        this.service.resume().subscribe({ next: (r) => this.resume.set(r) });
        this.snack.open(message(rendu), 'Fermer', { duration: 5000 });
      },
      error: (echec: unknown) => {
        this.enCours.set(null);
        this.erreur.set(messageDErreur(echec, 'L’opération n’a pas pu être enregistrée.'));
      },
    });
  }

  protected ouvrirLeFormulaire(): void {
    this.brouillon.set(brouillonVierge());
    this.erreurInscription.set(null);
    this.formulaireOuvert.set(true);
  }

  protected changerEntreprise(champ: string, valeur: unknown): void {
    this.brouillon.update((b) => ({ ...b, entreprise: { ...b.entreprise, [champ]: valeur } }));
  }

  protected changerAdresseEntreprise(champ: string, valeur: string): void {
    this.brouillon.update((b) => ({
      ...b,
      entreprise: { ...b.entreprise, adresse: { ...b.entreprise.adresse, [champ]: valeur } },
    }));
  }

  protected changerGerant(champ: string, valeur: unknown): void {
    this.brouillon.update((b) => ({
      ...b,
      administrateur: { ...b.administrateur, [champ]: valeur },
    }));
  }

  /**
   * Tout ce que le serveur exigera, verifié ici pour ne pas faire l'aller-retour pour rien.
   *
   * Six champs, et c'est tout ce qu'il faut : de quoi identifier le commerce, de quoi rappeler
   * son gérant, et de quoi le laisser entrer. Le reste — registre de commerce, NIU, adresse
   * complète, TVA — appartient au commerçant et se renseigne dans « Le magasin ».
   */
  protected readonly inscriptionComplete = computed(() => {
    const b = this.brouillon();
    const e = b.entreprise;
    const g = b.administrateur;
    return Boolean(e.nom && e.tel && g.nom && g.username && g.email && g.motdepasse);
  });

  protected inscrire(): void {
    if (this.inscriptionEnCours() || !this.inscriptionComplete()) {
      return;
    }
    this.inscriptionEnCours.set(true);
    this.erreurInscription.set(null);
    const brouillon = this.brouillon();
    this.service
      .inscrire(brouillon)
      .subscribe({
        next: (inscrite) => {
          this.inscriptionEnCours.set(false);
          this.formulaireOuvert.set(false);
          this.charger();
          this.snack.open(
            `${inscrite.nom} est inscrit. Ses accès partent par courriel à ${brouillon.administrateur.email}.`,
            'Fermer',
            { duration: 8000 },
          );
        },
        error: (echec: unknown) => {
          this.inscriptionEnCours.set(false);
          // Le message du serveur, tel quel : il nomme le champ en cause, ou dit que
          // l'identifiant est déjà pris. Un texte générique effacerait la seule information utile.
          this.erreurInscription.set(
            messageDErreur(echec, 'Le commerce n’a pas pu être inscrit.'),
          );
        },
      });
  }
}
