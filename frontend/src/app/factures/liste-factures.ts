import {
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Entreprise } from '../noyau/entreprise';
import { imprimerLeTicket } from '../noyau/impression';
import { Ticket } from '../ticket/ticket';
import type { EntrepriseDto } from '../noyau/api';
import {
  FactureDto,
  Factures,
  MODES_DE_REGLEMENT,
  ModeReglement,
  ReglementDto,
  libelleDuMode,
} from './factures.service';
import { Session } from '../noyau/session';
import { messageDErreur } from '../noyau/erreurs';
import type { Page } from '../noyau/api';

/** L'etiquette d'une facture, selon ce qu'il reste a encaisser. */
function pastilleDe(facture: FactureDto) {
  if (facture.annulee) {
    return {
      libelle: 'Annulée',
      fond: 'var(--mat-sys-surface-container-high)',
      texte: 'var(--mat-sys-on-surface-variant)',
    };
  }
  switch (facture.statutReglement) {
    case 'REGLEE':
      return {
        libelle: 'Réglée',
        fond: 'var(--mat-sys-secondary-container)',
        texte: 'var(--mat-sys-on-secondary-container)',
      };
    case 'PARTIELLEMENT_REGLEE':
      return {
        libelle: 'Partielle',
        fond: 'var(--mat-sys-tertiary-container)',
        texte: 'var(--mat-sys-on-tertiary-container)',
      };
    default:
      return {
        libelle: 'Impayée',
        fond: 'var(--mat-sys-error-container)',
        texte: 'var(--mat-sys-on-error-container)',
      };
  }
}

/**
 * Les factures, et ce qu'il reste a encaisser dessus.
 *
 * Ecran de bureau : un tableau, des filtres, et le detail dans un volet lateral plutot que sur une
 * autre page. Le comptable passe d'une facture a l'autre en encaissant ; le faire naviguer
 * aller-retour lui ferait perdre sa place dans la liste a chaque fois.
 */
@Component({
  selector: 'app-liste-factures',
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatTooltipModule,
    Ticket,
  ],
  templateUrl: './liste-factures.html',
})
export class ListeFactures implements OnInit {
  private readonly service = inject(Factures);
  private readonly magasinService = inject(Entreprise);
  private readonly destruction = inject(DestroyRef);
  private readonly session = inject(Session);
  private readonly snack = inject(MatSnackBar);
  private readonly frappe = new Subject<string>();

  protected readonly recherche = signal('');
  protected readonly statut = signal('DUES');
  protected readonly lignes = signal<FactureDto[]>([]);
  protected readonly total = signal(0);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);

  /** L'en-tete du magasin, pour le ticket. Chargee une fois et gardee par le service. */
  protected readonly magasin = signal<EntrepriseDto | null>(null);

  /**
   * La facture dont on montre le ticket avant de le ressortir.
   *
   * Toujours marque DUPLICATA : un ticket reimprime qui ne le dirait pas laisserait circuler deux
   * papiers identiques pour un seul encaissement.
   */
  protected readonly ticket = signal<FactureDto | null>(null);
  private readonly zoneTicket = viewChild<ElementRef<HTMLElement>>('zoneTicket');

  // Le volet de detail
  protected readonly ouverte = signal<FactureDto | null>(null);
  protected readonly reglements = signal<ReglementDto[]>([]);
  protected readonly chargementDetail = signal(false);

  // La saisie d'un encaissement
  protected readonly montant = signal<number | null>(null);
  protected readonly mode = signal<ModeReglement>('ESPECES');
  protected readonly reference = signal('');
  protected readonly envoiEnCours = signal(false);
  protected readonly erreurDetail = signal<string | null>(null);

  protected readonly modes = MODES_DE_REGLEMENT;
  protected readonly libelleDuMode = libelleDuMode;
  protected readonly pastille = pastilleDe;

  /** Reprendre un encaissement touche a une recette deja constatee. */
  protected readonly peutReprendre = computed(() =>
    this.session.roles().some((r) => ['ROLE_ADMIN', 'ROLE_COMPTABLE'].includes(r)),
  );
  protected readonly peutAnnuler = computed(() =>
    this.session.roles().some((r) => ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_COMPTABLE'].includes(r)),
  );

  /** Ce que la liste affichee represente : le comptable veut ce total sous les yeux. */
  protected readonly resteTotal = computed(() =>
    this.lignes().reduce((somme, f) => somme + (f.annulee ? 0 : (f.resteAPayer ?? 0)), 0),
  );

  constructor() {
    this.frappe
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((q) => this.service.lister(q, this.statut())),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (page) => this.afficher(page),
        error: () => this.echouer(),
      });
  }

  ngOnInit(): void {
    this.charger();
    this.magasinService
      .charger()
      .pipe(takeUntilDestroyed(this.destruction))
      .subscribe({
        // Sans identite, le ticket sortira sans en-tete plutot que pas du tout.
        next: (magasin) => this.magasin.set(magasin),
        error: () => this.magasin.set(null),
      });
  }

  /** Montre le ticket de cette facture, tel qu'il ressortira du rouleau. */
  protected reimprimer(facture: FactureDto): void {
    this.ticket.set(facture);
  }

  protected imprimerLeDuplicata(): void {
    const zone = this.zoneTicket()?.nativeElement;
    if (zone) {
      imprimerLeTicket(zone);
    }
  }

  protected fermerLeTicket(): void {
    this.ticket.set(null);
  }

  protected chercher(q: string): void {
    this.recherche.set(q);
    this.chargement.set(true);
    this.frappe.next(q);
  }

  protected changerStatut(statut: string): void {
    this.statut.set(statut);
    this.charger();
  }

  protected ouvrir(facture: FactureDto): void {
    this.ouverte.set(facture);
    this.reinitialiserLaSaisie();
    this.chargementDetail.set(true);
    this.service.detail(facture.id!).subscribe({
      next: (detail) => {
        this.ouverte.set(detail);
        this.chargementDetail.set(false);
      },
      error: () => this.chargementDetail.set(false),
    });
    this.service.reglements(facture.id!).subscribe({
      next: (liste) => this.reglements.set(liste),
      error: () => this.reglements.set([]),
    });
  }

  protected fermer(): void {
    this.ouverte.set(null);
    this.reglements.set([]);
    this.reinitialiserLaSaisie();
  }

  /** Remplit le montant avec ce qui reste : le geste ordinaire est de solder. */
  protected soldeEntier(): void {
    this.montant.set(this.ouverte()?.resteAPayer ?? null);
  }

  protected encaisser(): void {
    const facture = this.ouverte();
    const montant = this.montant();
    if (!facture || this.envoiEnCours() || !montant || montant <= 0) {
      return;
    }
    this.envoiEnCours.set(true);
    this.erreurDetail.set(null);

    this.service
      .regler(facture.id!, {
        montant,
        mode: this.mode(),
        reference: this.reference() || undefined,
      })
      .subscribe({
        next: () => {
          this.envoiEnCours.set(false);
          this.snack.open('Encaissement enregistré.', 'Fermer', { duration: 3000 });
          // La facture et la liste bougent ensemble : le reste a payer vient de changer, et un
          // statut qui reste « impayee » apres un encaissement passerait pour un bogue.
          this.ouvrir(facture);
          this.charger();
        },
        error: (echec: unknown) => {
          this.envoiEnCours.set(false);
          this.erreurDetail.set(messageDErreur(echec, "L'encaissement n'a pas pu être enregistré."));
        },
      });
  }

  protected reprendre(reglement: ReglementDto): void {
    const facture = this.ouverte();
    if (!facture) {
      return;
    }
    this.service.supprimerReglement(facture.id!, reglement.id!).subscribe({
      next: () => {
        this.snack.open('Encaissement repris.', 'Fermer', { duration: 3000 });
        this.ouvrir(facture);
        this.charger();
      },
      error: (echec: unknown) =>
        this.erreurDetail.set(messageDErreur(echec, "L'encaissement n'a pas pu être repris.")),
    });
  }

  protected annuler(): void {
    const facture = this.ouverte();
    if (!facture) {
      return;
    }
    this.service.annuler(facture.id!).subscribe({
      next: () => {
        this.snack.open('Facture annulée.', 'Fermer', { duration: 3000 });
        this.ouvrir(facture);
        this.charger();
      },
      error: (echec: unknown) =>
        this.erreurDetail.set(messageDErreur(echec, "La facture n'a pas pu être annulée.")),
    });
  }

  private reinitialiserLaSaisie(): void {
    this.montant.set(null);
    this.mode.set('ESPECES');
    this.reference.set('');
    this.erreurDetail.set(null);
  }

  private charger(): void {
    this.chargement.set(true);
    this.erreur.set(null);
    this.service.lister(this.recherche(), this.statut()).subscribe({
      next: (page) => this.afficher(page),
      error: () => this.echouer(),
    });
  }

  private afficher(page: Page<FactureDto>): void {
    this.lignes.set(page.content ?? []);
    this.total.set(page.totalElements ?? 0);
    this.chargement.set(false);
  }

  private echouer(): void {
    this.chargement.set(false);
    this.erreur.set('Les factures n’ont pas pu être chargées.');
  }
}
