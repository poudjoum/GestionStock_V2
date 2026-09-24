import {
  Component,
  ElementRef,
  OnInit,
  computed,
  effect,
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
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { messageDErreur } from '../noyau/erreurs';
import {
  Inventaire as ServiceInventaire,
  LigneComptageDto,
  SeanceInventaireDto,
  VueDesLignes,
} from './inventaire.service';

/**
 * L'inventaire : compter le magasin, voir les ecarts, les rattraper.
 *
 * <b>Qui, et ou.</b> Le magasinier, debout dans les rayons, une douchette dans une main et un
 * telephone dans l'autre. C'est ce qui decide de l'ecran : un seul geste a la fois, de grandes
 * cibles, et le compteur de ce qui reste toujours visible.
 *
 * <b>Le geste.</b> Scanner, taper une quantite, passer au suivant. Rien d'autre ne doit demander
 * d'attention, et surtout pas la souris : le champ du code reprend le point apres chaque article,
 * comme au comptoir.
 *
 * <b>La douchette.</b> Un lecteur USB est un clavier : il tape le code dans le champ qui a le
 * point, puis envoie Entree. Il n'y a donc ni camera, ni bibliotheque, ni permission a demander.
 *
 * Rien de ce qui est compte ne touche au stock : c'est la validation, et elle seule, qui pose les
 * mouvements de correction. Tant qu'on n'a pas valide, tout se reprend.
 */
@Component({
  selector: 'app-inventaire',
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  templateUrl: './inventaire.html',
})
export class InventaireEcran implements OnInit {
  private readonly service = inject(ServiceInventaire);
  private readonly snack = inject(MatSnackBar);
  private readonly frappe = new Subject<void>();

  protected readonly chargement = signal(true);
  protected readonly seance = signal<SeanceInventaireDto | null>(null);
  protected readonly erreur = signal<string | null>(null);

  // L'ouverture
  protected readonly commentaire = signal('');
  protected readonly ouvertureEnCours = signal(false);
  protected readonly historique = signal<SeanceInventaireDto[]>([]);

  // Le comptage
  protected readonly lignes = signal<LigneComptageDto[]>([]);
  protected readonly vue = signal<VueDesLignes>('A_COMPTER');
  protected readonly recherche = signal('');
  protected readonly cherche = signal(false);

  /**
   * L'article qu'on tient en main, entre le scan et la quantite.
   *
   * Deux temps plutot qu'un : la douchette rend un code, pas une quantite. Tant que cette ligne
   * est posee, l'ecran ne demande qu'une chose — combien y en a-t-il.
   */
  protected readonly enCours = signal<LigneComptageDto | null>(null);
  protected readonly quantite = signal<number | null>(null);
  protected readonly envoiEnCours = signal(false);
  protected readonly codeInconnu = signal<string | null>(null);

  /** La ligne qui vient d'etre notee, le temps d'un clignotement. */
  protected readonly dernierComptage = signal<number | null>(null);
  private clignotement?: ReturnType<typeof setTimeout>;

  // La cloture
  protected readonly confirmation = signal<'validation' | 'abandon' | null>(null);
  protected readonly clotureEnCours = signal(false);

  private readonly champCode = viewChild<ElementRef<HTMLInputElement>>('champCode');
  private readonly champQuantite = viewChild<ElementRef<HTMLInputElement>>('champQuantite');

  protected readonly restants = computed(() => {
    const s = this.seance();
    return s ? (s.articles ?? 0) - (s.comptes ?? 0) : 0;
  });

  protected readonly avancement = computed(() => {
    const s = this.seance();
    const total = s?.articles ?? 0;
    return total === 0 ? 0 : Math.round(((s?.comptes ?? 0) / total) * 100);
  });

  constructor() {
    this.frappe
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap(() => this.service.lignes(this.seance()!.id!, this.recherche(), this.vue())),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (page) => {
          this.cherche.set(false);
          this.lignes.set(page.content ?? []);
        },
        error: () => this.cherche.set(false),
      });

    // Le point va la ou le geste suivant doit avoir lieu : sur la quantite quand un article vient
    // d'etre reconnu, sur le code le reste du temps. Sans cela, le magasinier repose sa douchette
    // pour toucher l'ecran a chaque article.
    effect(() => {
      const article = this.enCours();
      queueMicrotask(() => {
        const champ = article ? this.champQuantite() : this.champCode();
        champ?.nativeElement.focus();
        champ?.nativeElement.select?.();
      });
    });
  }

  ngOnInit(): void {
    this.service.ouverte().subscribe({
      next: (seance) => {
        this.seance.set(seance);
        this.chargement.set(false);
        if (seance) {
          this.rafraichirLesLignes();
        } else {
          this.chargerLHistorique();
        }
      },
      error: (echec: unknown) => {
        this.erreur.set(messageDErreur(echec, 'L’inventaire n’a pas pu être lu.'));
        this.chargement.set(false);
      },
    });
  }

  private chargerLHistorique(): void {
    this.service.historique().subscribe({
      next: (page) => this.historique.set(page.content ?? []),
      error: () => this.historique.set([]),
    });
  }

  protected ouvrir(): void {
    if (this.ouvertureEnCours()) {
      return;
    }
    this.ouvertureEnCours.set(true);
    this.erreur.set(null);
    this.service.ouvrir(this.commentaire().trim()).subscribe({
      next: (seance) => {
        this.ouvertureEnCours.set(false);
        this.seance.set(seance);
        this.commentaire.set('');
        this.rafraichirLesLignes();
        this.snack.open(
          `Séance ${seance.reference} ouverte sur ${seance.articles} article(s).`,
          'Fermer',
          { duration: 5000 },
        );
      },
      error: (echec: unknown) => {
        this.ouvertureEnCours.set(false);
        this.erreur.set(messageDErreur(echec, 'La séance n’a pas pu être ouverte.'));
      },
    });
  }

  protected changerDeVue(vue: VueDesLignes): void {
    this.vue.set(vue);
    this.rafraichirLesLignes();
  }

  protected chercher(q: string): void {
    this.recherche.set(q);
    this.codeInconnu.set(null);
    this.cherche.set(true);
    this.frappe.next();
  }

  private rafraichirLesLignes(): void {
    const seance = this.seance();
    if (!seance?.id) {
      return;
    }
    this.cherche.set(true);
    this.service.lignes(seance.id, this.recherche(), this.vue()).subscribe({
      next: (page) => {
        this.cherche.set(false);
        this.lignes.set(page.content ?? []);
      },
      error: () => this.cherche.set(false),
    });
  }

  /**
   * Entree dans le champ du code : c'est ce qu'envoie la douchette apres sa rafale.
   *
   * On ne resout que par code exact. Le meme champ sert a filtrer la liste par le nom, et c'est
   * voulu : deux champs cote a cote obligeraient a choisir avant chaque geste.
   */
  protected valider(): void {
    const saisi = this.recherche().trim();
    if (!saisi || this.envoiEnCours()) {
      return;
    }
    const connue = this.lignes().find(
      (l) => (l.codeArticle ?? '').toLowerCase() === saisi.toLowerCase(),
    );
    if (connue) {
      this.prendreEnMain(connue);
      return;
    }
    // Pas dans la page affichee : on le demande au serveur, qui cherche dans toute la seance.
    this.envoiEnCours.set(true);
    this.service.lignes(this.seance()!.id!, saisi, 'TOUTES', 0, 5).subscribe({
      next: (page) => {
        this.envoiEnCours.set(false);
        const ligne = (page.content ?? []).find(
          (l) => (l.codeArticle ?? '').toLowerCase() === saisi.toLowerCase(),
        );
        if (ligne) {
          this.prendreEnMain(ligne);
        } else {
          // Dit sans bloquer : on ne perd pas un comptage parce qu'un article manque.
          this.codeInconnu.set(saisi);
          this.champCode()?.nativeElement.select();
        }
      },
      error: () => {
        this.envoiEnCours.set(false);
        this.codeInconnu.set(saisi);
      },
    });
  }

  protected prendreEnMain(ligne: LigneComptageDto): void {
    this.codeInconnu.set(null);
    this.enCours.set(ligne);
    this.quantite.set(ligne.quantiteComptee ?? null);
  }

  protected abandonnerLArticle(): void {
    this.enCours.set(null);
    this.quantite.set(null);
    this.recherche.set('');
  }

  /** Enregistre la quantite trouvee et rend le point au champ du code. */
  protected noter(): void {
    const ligne = this.enCours();
    const quantite = this.quantite();
    if (!ligne || quantite === null || quantite < 0 || this.envoiEnCours()) {
      return;
    }
    this.envoiEnCours.set(true);
    this.erreur.set(null);
    this.service
      .compter(this.seance()!.id!, { idArticle: ligne.idArticle }, quantite)
      .subscribe({
        next: (notee) => {
          this.envoiEnCours.set(false);
          this.enCours.set(null);
          this.quantite.set(null);
          this.recherche.set('');
          this.clignoter(notee.id);
          this.rafraichirLeTout();
        },
        error: (echec: unknown) => {
          this.envoiEnCours.set(false);
          this.erreur.set(messageDErreur(echec, 'Le comptage n’a pas pu être enregistré.'));
        },
      });
  }

  protected reprendre(ligne: LigneComptageDto): void {
    if (!ligne.id || this.envoiEnCours()) {
      return;
    }
    this.envoiEnCours.set(true);
    this.service.annulerComptage(this.seance()!.id!, ligne.id).subscribe({
      next: () => {
        this.envoiEnCours.set(false);
        this.rafraichirLeTout();
      },
      error: (echec: unknown) => {
        this.envoiEnCours.set(false);
        this.erreur.set(messageDErreur(echec, 'Le comptage n’a pas pu être repris.'));
      },
    });
  }

  private clignoter(idLigne: number | undefined): void {
    this.dernierComptage.set(idLigne ?? null);
    clearTimeout(this.clignotement);
    this.clignotement = setTimeout(() => this.dernierComptage.set(null), 900);
  }

  /** Les compteurs et la liste : ce qu'on vient de compter change les deux. */
  private rafraichirLeTout(): void {
    const seance = this.seance();
    if (!seance?.id) {
      return;
    }
    this.service.seance(seance.id).subscribe({ next: (s) => this.seance.set(s) });
    this.rafraichirLesLignes();
  }

  protected cloturer(): void {
    const quoi = this.confirmation();
    const seance = this.seance();
    if (!quoi || !seance?.id || this.clotureEnCours()) {
      return;
    }
    this.clotureEnCours.set(true);
    const appel =
      quoi === 'validation'
        ? this.service.valider(seance.id)
        : this.service.abandonner(seance.id);
    appel.subscribe({
      next: (close) => {
        this.clotureEnCours.set(false);
        this.confirmation.set(null);
        this.seance.set(null);
        this.lignes.set([]);
        this.vue.set('A_COMPTER');
        this.recherche.set('');
        this.chargerLHistorique();
        this.snack.open(
          quoi === 'validation'
            ? `Séance ${close.reference} validée : ${close.ecarts} écart(s) rattrapé(s).`
            : `Séance ${close.reference} abandonnée : le stock n’a pas bougé.`,
          'Fermer',
          { duration: 6000 },
        );
      },
      error: (echec: unknown) => {
        this.clotureEnCours.set(false);
        this.erreur.set(messageDErreur(echec, 'La séance n’a pas pu être clôturée.'));
      },
    });
  }
}
