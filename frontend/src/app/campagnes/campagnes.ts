import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, catchError, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import { Campagnes, CampagneDto } from './campagnes.service';
import { Entreprise } from '../noyau/entreprise';
import { messageDErreur } from '../noyau/erreurs';
import { STATUTS_CAMPAGNE } from '../noyau/statuts';
import { EnTetePage, EtatVide, OptionSelecteur, Selecteur, Statut } from '../design';
import { preparerUneImage } from '../parametres/logo';
import { jourLocal, prixPromotionnel, PromotionArticleDto } from '../comptoir/prix-promotionnel';
import type { ArticleDto } from '../noyau/api';

type Ligne = Partial<PromotionArticleDto>;

/** Une campagne neuve : une semaine a partir d'aujourd'hui, sans article. */
function campagneVierge(): CampagneDto {
  const debut = new Date();
  const fin = new Date();
  fin.setDate(fin.getDate() + 6);
  return { titre: '', message: '', dateDebut: jourLocal(debut), dateFin: jourLocal(fin), promotions: [] };
}

/**
 * Les campagnes de promotion du magasin.
 *
 * Le gerant y prepare ce que ses clients verront dans l'application, et ce que la caisse vendra
 * moins cher : une periode, un message, et des articles reduits d'un pourcentage ou a un prix
 * fixe. A cote de chaque article, le prix qu'il affichera en rayon — c'est ce prix-la qu'on
 * decide, pas un pourcentage abstrait.
 *
 * Sur grand ecran, la liste a gauche et la campagne ouverte a droite : on en compare deux sans
 * perdre sa place. Sur telephone, l'une puis l'autre.
 */
@Component({
  selector: 'app-campagnes',
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    EnTetePage,
    EtatVide,
    Selecteur,
    Statut,
  ],
  templateUrl: './campagnes.html',
  styleUrl: './campagnes.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CampagnesEcran implements OnInit {
  private readonly service = inject(Campagnes);
  private readonly magasin = inject(Entreprise);
  private readonly snack = inject(MatSnackBar);

  protected readonly statuts = STATUTS_CAMPAGNE;

  /** Pourcentage ou prix fixe : deux facons de dire le meme prix, au choix du gerant. */
  protected readonly typesRemise: OptionSelecteur<PromotionArticleDto['typeRemise']>[] = [
    { valeur: 'POURCENTAGE', libelle: '%' },
    { valeur: 'PRIX_FIXE', libelle: 'Prix' },
  ];

  protected readonly chargement = signal(true);
  protected readonly erreurDeChargement = signal<string | null>(null);
  protected readonly campagnes = signal<CampagneDto[]>([]);

  /** La campagne ouverte, en brouillon : rien ne part avant « Enregistrer ». */
  protected readonly brouillon = signal<CampagneDto | null>(null);
  protected readonly envoi = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurs = signal<string[]>([]);
  /** L'arret se confirme d'un second geste : il rend aussitot les prix normaux en caisse. */
  protected readonly confirmerArret = signal(false);

  protected readonly recherche = signal('');
  protected readonly resultats = signal<ArticleDto[]>([]);
  private readonly frappe = new Subject<string>();

  /** Une campagne finie ou arretee ne se modifie plus : des tickets citent ses prix. */
  protected readonly lectureSeule = computed(() => {
    const statut = this.brouillon()?.statut;
    return statut === 'TERMINEE' || statut === 'ARRETEE';
  });
  /** Une campagne commencee garde son debut : des ventes ont eu lieu a ses prix. */
  protected readonly debutFige = computed(() => this.brouillon()?.statut === 'EN_COURS');
  protected readonly arretable = computed(() => {
    const statut = this.brouillon()?.statut;
    return statut === 'EN_COURS' || statut === 'A_VENIR';
  });

  protected readonly valide = computed(() => {
    const c = this.brouillon();
    return (
      !!c &&
      c.titre.trim().length > 0 &&
      !!c.dateDebut &&
      !!c.dateFin &&
      c.dateFin >= c.dateDebut &&
      c.promotions.every((p) => (p.valeur ?? 0) > 0 && this.prixPromo(p) < (p.prixNormalHt ?? 0))
    );
  });

  constructor() {
    this.frappe
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((q) =>
          q.trim().length < 2
            ? of([])
            : this.service.chercherArticles(q).pipe(catchError(() => of([]))),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((articles) => this.resultats.set(articles));
  }

  ngOnInit(): void {
    // Le taux de TVA du magasin, pour annoncer les prix tels qu'ils seront en rayon.
    this.magasin.charger().subscribe({ error: () => undefined });
    this.charger();
  }

  private charger(idAOuvrir?: number): void {
    this.service.lister().subscribe({
      next: (campagnes) => {
        this.campagnes.set(campagnes);
        this.chargement.set(false);
        if (idAOuvrir) {
          const ouverte = campagnes.find((c) => c.id === idAOuvrir);
          if (ouverte) {
            this.ouvrir(ouverte);
          }
        }
      },
      error: (echec: unknown) => {
        this.erreurDeChargement.set(messageDErreur(echec, 'Les campagnes n’ont pas pu être lues.'));
        this.chargement.set(false);
      },
    });
  }

  protected ouvrir(campagne: CampagneDto): void {
    this.brouillon.set(structuredClone(campagne));
    this.erreur.set(null);
    this.erreurs.set([]);
    this.confirmerArret.set(false);
    this.chercher('');
  }

  protected nouvelle(): void {
    this.ouvrir(campagneVierge());
  }

  protected fermer(): void {
    this.brouillon.set(null);
  }

  protected changer<C extends keyof CampagneDto>(champ: C, valeur: CampagneDto[C]): void {
    this.brouillon.update((c) => (c ? { ...c, [champ]: valeur } : c));
  }

  protected chercher(q: string): void {
    this.recherche.set(q);
    this.frappe.next(q);
  }

  protected dejaAjoute(article: ArticleDto): boolean {
    return !!this.brouillon()?.promotions.some((p) => p.idArticle === article.id);
  }

  /** Ajoute l'article a dix pour cent : un point de depart, que le gerant corrige aussitot. */
  protected ajouter(article: ArticleDto): void {
    if (this.dejaAjoute(article)) {
      return;
    }
    const ligne: Ligne = {
      idArticle: article.id,
      designation: article.designation,
      codeArticle: article.codeArticle,
      prixNormalHt: article.prixUnitaireHt,
      tauxTva: article.tauxTva,
      typeRemise: 'POURCENTAGE',
      valeur: 10,
    };
    this.brouillon.update((c) => (c ? { ...c, promotions: [...c.promotions, ligne] } : c));
    this.chercher('');
  }

  protected changerLigne(index: number, modif: Ligne): void {
    this.brouillon.update((c) =>
      c
        ? {
            ...c,
            promotions: c.promotions.map((p, i) => (i === index ? { ...p, ...modif } : p)),
          }
        : c,
    );
  }

  /** Passer du pourcentage au prix fixe garde le meme prix promotionnel, et inversement. */
  protected changerType(index: number, ligne: Ligne, type: PromotionArticleDto['typeRemise']): void {
    if (ligne.typeRemise === type) {
      return;
    }
    const normal = ligne.prixNormalHt ?? 0;
    const promo = this.prixPromo(ligne);
    const valeur =
      type === 'PRIX_FIXE'
        ? promo
        : normal > 0
          ? Math.round((1 - promo / normal) * 100)
          : 10;
    this.changerLigne(index, { typeRemise: type, valeur });
  }

  protected retirer(index: number): void {
    this.brouillon.update((c) =>
      c ? { ...c, promotions: c.promotions.filter((_, i) => i !== index) } : c,
    );
  }

  protected prixPromo(ligne: Ligne): number {
    if (!ligne.typeRemise || ligne.valeur == null) {
      return ligne.prixNormalHt ?? 0;
    }
    return prixPromotionnel(ligne.prixNormalHt ?? 0, ligne.typeRemise, Number(ligne.valeur));
  }

  /** Le prix en rayon, TVA comprise : la regle du serveur — le magasin d'abord, l'article ensuite. */
  protected ttc(ht: number, ligne: Ligne): number {
    const magasin = this.magasin.mienne();
    const taux =
      magasin?.assujettieTva === false ? 0 : (ligne.tauxTva ?? magasin?.tauxTva ?? 0);
    // `ht * (100 + taux) / 100` et non `ht * (1 + taux / 100)` : 1,1925 ne s'ecrit pas exactement
    // en binaire, et 5 000 F a 19,25 % donnaient 5 962,4999… — arrondis a 5 962 la ou le serveur,
    // qui calcule en decimal, annonce 5 963.
    return Math.round((ht * (100 + taux)) / 100);
  }

  protected async choisirLImage(evenement: Event): Promise<void> {
    const champ = evenement.target as HTMLInputElement;
    const fichier = champ.files?.[0];
    champ.value = '';
    if (!fichier) {
      return;
    }
    try {
      this.changer('image', await preparerUneImage(fichier, 800, 250_000));
    } catch (echec: unknown) {
      this.erreur.set(echec instanceof Error ? echec.message : 'Cette image n’a pas pu être lue.');
    }
  }

  protected enregistrer(): void {
    const campagne = this.brouillon();
    if (!campagne || this.envoi() || !this.valide()) {
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    this.erreurs.set([]);
    this.service.enregistrer(campagne).subscribe({
      next: (enregistree) => {
        this.envoi.set(false);
        this.snack.open(
          campagne.id ? 'Campagne enregistrée.' : 'Campagne créée — la caisse l’appliquera dès son premier jour.',
          'Fermer',
          { duration: 4000 },
        );
        this.charger(enregistree.id);
      },
      error: (echec: unknown) => this.echec(echec, 'La campagne n’a pas pu être enregistrée.'),
    });
  }

  protected arreter(): void {
    const id = this.brouillon()?.id;
    if (!id || this.envoi()) {
      return;
    }
    if (!this.confirmerArret()) {
      this.confirmerArret.set(true);
      return;
    }
    this.envoi.set(true);
    this.service.arreter(id).subscribe({
      next: () => {
        this.envoi.set(false);
        this.snack.open('Campagne arrêtée — les prix normaux reprennent en caisse.', 'Fermer', {
          duration: 4000,
        });
        this.charger(id);
      },
      error: (echec: unknown) => this.echec(echec, 'La campagne n’a pas pu être arrêtée.'),
    });
  }

  /** Le message du serveur, et la liste de ce qu'il refuse : elle nomme l'article en cause. */
  private echec(echec: unknown, repli: string): void {
    this.envoi.set(false);
    this.confirmerArret.set(false);
    const corps = (echec as { error?: { message?: string; errors?: string[] } })?.error;
    const detail = Array.isArray(corps?.errors) ? corps.errors : [];
    // Avec ses precisions, le message s'affiche seul et les precisions en liste, une par ligne :
    // trois articles refuses se lisent mieux ainsi qu'enchaines entre parentheses.
    this.erreur.set(detail.length && corps?.message ? corps.message : messageDErreur(echec, repli));
    this.erreurs.set(detail);
  }
}
