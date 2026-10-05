import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MotifMvtStk, Stock } from './stock.service';
import { Session } from '../noyau/session';
import { Sites } from '../noyau/sites';
import { messageDErreur } from '../noyau/erreurs';
import { enConditionnements, symbole, unites } from '../noyau/conditionnements';
import type { LigneInventaireDto } from '../noyau/api';
import { statutDuStock } from '../noyau/statuts';
import { EnTetePage, EtatVide, OptionSelecteur, Section, Selecteur, Statut } from '../design';

/**
 * L'etat du stock, tel qu'on le consulte debout dans les rayons.
 *
 * Une recherche, une liste, rien d'autre. Le magasinier cherche un article precis et veut savoir
 * combien il en reste : lui servir un tableau de valorisation a faire defiler serait lui donner
 * l'inventaire du comptable sur un ecran de cinq pouces.
 *
 * La frappe est temporisee : un caractere par requete ferait huit allers-retours pour « ciment »,
 * ce qui se voit sur une connexion de telephone.
 */
@Component({
  selector: 'app-etat-du-stock',
  imports: [
    DecimalPipe,
    FormsModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatButtonModule,
    MatSelectModule,
    RouterLink,
    EnTetePage,
    EtatVide,
    Section,
    Selecteur,
    Statut,
  ],
  templateUrl: './etat-du-stock.html',
  styles: `
    .unite-base {
      font-weight: 400;
      font-size: var(--gs-texte-xs);
      color: var(--gs-encre-3);
    }

    .repartition {
      display: flex;
      flex-wrap: wrap;
      gap: 2px 10px;
      margin-top: 2px;
      font-size: var(--gs-texte-xs);
      color: var(--gs-encre-3);
    }

    .en-cartons {
      display: block;
      font-weight: 400;
      font-size: var(--gs-texte-xs);
      color: var(--gs-encre-3);
    }

    /* Le volet d'ajustement : en bas sur telephone, a droite sur grand ecran. */
    .ajustement-fond {
      position: fixed;
      inset: 0;
      z-index: 20;
      background: rgb(0 0 0 / 0.32);
    }

    .ajustement {
      position: fixed;
      z-index: 21;
      inset: auto 0 0 0;
      max-height: 90dvh;
      overflow-y: auto;
      padding: 16px;
      border-radius: 16px 16px 0 0;
      background: var(--gs-surface);
      box-shadow: var(--gs-ombre-2);
    }

    @media (min-width: 768px) {
      .ajustement {
        inset: 0 0 0 auto;
        width: 400px;
        max-height: none;
        border-radius: 0;
      }
    }
  `,
})
export class EtatDuStock implements OnInit {
  private readonly stock = inject(Stock);
  private readonly session = inject(Session);
  private readonly snack = inject(MatSnackBar);
  private readonly frappe = new Subject<string>();

  private readonly sites = inject(Sites);
  /** Le site dont on lit le stock, et la vue « tous sites » pour qui voit l'entreprise entiere. */
  protected readonly siteActif = this.sites.actif;
  protected readonly peutVoirTout = computed(() => this.sites.tousLesSites() && this.sites.plusieurs());
  protected readonly tousSites = signal(false);
  protected readonly portees = computed<OptionSelecteur<'site' | 'tous'>[]>(() => [
    { valeur: 'site', libelle: this.siteActif()?.nom ?? 'Ce site' },
    { valeur: 'tous', libelle: 'Tous les sites' },
  ]);
  protected readonly portee = computed<'site' | 'tous'>(() => (this.tousSites() ? 'tous' : 'site'));

  protected changerPortee(portee: 'site' | 'tous'): void {
    this.tousSites.set(portee === 'tous');
    this.charger();
    this.stock.alertes(this.tousSites()).subscribe({
      next: (lignes) => this.nombreAlertes.set(lignes.length),
      error: () => undefined,
    });
  }

  protected readonly symbole = symbole;
  protected readonly unites = unites;
  protected readonly enConditionnements = enConditionnements;
  protected readonly motifs = MOTIFS;

  /** Ceux qui tiennent la marchandise : la route des mouvements ne s'ouvre qu'a eux. */
  protected readonly peutAjuster = computed(() =>
    this.session.roles().some((r) => ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER'].includes(r)),
  );

  /** L'article dont on declare une casse, une perte ou un retour ; nul, le volet est ferme. */
  protected readonly ajustement = signal<LigneInventaireDto | null>(null);
  protected readonly motif = signal<MotifMvtStk>('CASSE');
  protected readonly quantiteAjustee = signal<number | null>(null);
  /** 0 : l'unite de base. Pas `null`, que le selecteur de Material affiche comme un champ vide. */
  protected readonly unite = signal<number>(0);
  protected readonly envoiAjustement = signal(false);
  protected readonly erreurAjustement = signal<string | null>(null);

  /** Ce que l'ajustement fera au stock, en unites de base : on le lit avant de valider. */
  protected readonly effet = computed(() => {
    const ligne = this.ajustement();
    const quantite = this.quantiteAjustee();
    if (!ligne || !quantite || quantite <= 0) {
      return null;
    }
    const contenance = ligne.conditionnements?.find((c) => c.id === this.unite())?.quantiteUnites ?? 1;
    const mouvement = quantite * contenance;
    const entree = MOTIFS.find((m) => m.valeur === this.motif())?.sens === 'entree';
    return { mouvement, apres: (ligne.quantite ?? 0) + (entree ? mouvement : -mouvement) };
  });

  protected readonly recherche = signal('');
  protected readonly alertesSeulement = signal(false);
  protected readonly lignes = signal<LigneInventaireDto[]>([]);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);
  protected readonly total = signal(0);

  /**
   * Le nombre d'articles a recommander, lu une fois a l'ouverture : il s'affiche sur l'onglet,
   * pour qu'on sache avant de cliquer s'il y a quelque chose dedans.
   */
  private readonly nombreAlertes = signal<number | null>(null);
  private readonly nombreArticles = signal<number | null>(null);

  protected readonly vue = computed<'tout' | 'alertes'>(() => (this.alertesSeulement() ? 'alertes' : 'tout'));
  protected readonly vues = computed<OptionSelecteur<'tout' | 'alertes'>[]>(() => [
    { valeur: 'tout', libelle: 'Tout', compteur: this.nombreArticles() },
    { valeur: 'alertes', libelle: 'À recommander', compteur: this.nombreAlertes() },
  ]);

  constructor() {
    this.frappe
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((q) => this.stock.inventaire(q, 0, 25, this.tousSites())),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (page) => this.afficher(page.content ?? [], page.totalElements ?? 0),
        error: () => this.echouer(),
      });
  }

  ngOnInit(): void {
    this.charger();
    this.stock.alertes().subscribe({
      next: (lignes) => this.nombreAlertes.set(lignes.length),
      error: () => undefined,
    });
  }

  protected changerVue(vue: 'tout' | 'alertes'): void {
    this.basculerAlertes(vue === 'alertes');
  }

  protected chercher(q: string): void {
    this.recherche.set(q);
    if (this.alertesSeulement()) {
      // La recherche reprend la main : on ne cherche pas dans une liste deja restreinte sans le
      // dire, cela donnerait des resultats inexplicables.
      this.alertesSeulement.set(false);
    }
    this.chargement.set(true);
    this.frappe.next(q);
  }

  protected basculerAlertes(alertes: boolean): void {
    this.alertesSeulement.set(alertes);
    this.charger();
  }

  protected statut(ligne: LigneInventaireDto) {
    return statutDuStock(ligne);
  }

  /** Une quantite negative se lit d'un coup d'oeil : c'est elle qui demande un comptage. */
  protected estAnormal(ligne: LigneInventaireDto): boolean {
    return ligne.statut === 'NEGATIF' || ligne.statut === 'RUPTURE';
  }

  protected ouvrirAjustement(ligne: LigneInventaireDto): void {
    this.ajustement.set(ligne);
    this.motif.set('CASSE');
    this.quantiteAjustee.set(null);
    this.unite.set(0);
    this.erreurAjustement.set(null);
  }

  protected fermerAjustement(): void {
    this.ajustement.set(null);
  }

  protected ajuster(): void {
    const ligne = this.ajustement();
    const quantite = this.quantiteAjustee();
    if (!ligne || !quantite || quantite <= 0 || this.envoiAjustement()) {
      return;
    }
    const motif = MOTIFS.find((m) => m.valeur === this.motif())!;
    this.envoiAjustement.set(true);
    this.erreurAjustement.set(null);
    this.stock
      .ajuster(motif.sens, {
        article: { id: ligne.idArticle },
        quantite,
        motif: motif.valeur,
        conditionnement: this.unite() === 0 ? undefined : { id: this.unite() },
      })
      .subscribe({
        next: () => {
          this.envoiAjustement.set(false);
          this.snack.open(`${motif.libelle} enregistrée : ${ligne.designation}.`, 'Fermer', { duration: 4000 });
          this.ajustement.set(null);
          this.charger();
        },
        error: (echec: unknown) => {
          this.envoiAjustement.set(false);
          // « Stock insuffisant : 3 en magasin, 24 demandés » — le serveur dit pourquoi.
          this.erreurAjustement.set(messageDErreur(echec, 'L’ajustement n’a pas pu être enregistré.'));
        },
      });
  }

  private charger(): void {
    this.chargement.set(true);
    this.erreur.set(null);
    if (this.alertesSeulement()) {
      this.stock.alertes(this.tousSites()).subscribe({
        next: (lignes) => this.afficher(lignes, lignes.length),
        error: () => this.echouer(),
      });
      return;
    }
    this.stock.inventaire(this.recherche(), 0, 25, this.tousSites()).subscribe({
      next: (page) => this.afficher(page.content ?? [], page.totalElements ?? 0),
      error: () => this.echouer(),
    });
  }

  private afficher(lignes: LigneInventaireDto[], total: number): void {
    this.lignes.set(lignes);
    this.total.set(total);
    // Le total du catalogue, pour l'onglet « Tout », tant qu'aucune recherche ne le restreint.
    if (!this.alertesSeulement() && !this.recherche()) {
      this.nombreArticles.set(total);
    }
    this.chargement.set(false);
  }

  private echouer(): void {
    this.chargement.set(false);
    this.erreur.set('Le stock n’a pas pu être chargé.');
  }
}

/**
 * Ce qu'on declare a la main, et dans quel sens. La casse, la perte et la peremption forment la
 * demarque : ce que le magasin perd sans le vendre, et qu'il faut pouvoir mesurer.
 */
const MOTIFS: { valeur: MotifMvtStk; libelle: string; sens: 'entree' | 'sortie' }[] = [
  { valeur: 'CASSE', libelle: 'Casse', sens: 'sortie' },
  { valeur: 'PEREMPTION', libelle: 'Péremption', sens: 'sortie' },
  { valeur: 'PERTE', libelle: 'Perte ou vol', sens: 'sortie' },
  { valeur: 'RETOUR_FOURNISSEUR', libelle: 'Retour au fournisseur', sens: 'sortie' },
  { valeur: 'CONSOMMATION_INTERNE', libelle: 'Usage interne', sens: 'sortie' },
  { valeur: 'RETOUR_CLIENT', libelle: 'Retour d’un client', sens: 'entree' },
];
