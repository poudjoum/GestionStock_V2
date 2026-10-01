import {
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import {
  Observable,
  Subject,
  catchError,
  debounceTime,
  distinctUntilChanged,
  interval,
  map,
  of,
  switchMap,
  throwError,
} from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { BonDAchatDto, Comptoir, FactureDto, ModeReglement, VenteDto } from './comptoir.service';
import { CatalogueLocal } from './catalogue-local';
import { FileDesVentes, VenteSynchronisee } from './file-des-ventes';
import { messageDErreur } from '../noyau/erreurs';
import { Reseau, estUneCoupure } from '../noyau/reseau';
import { Entreprise } from '../noyau/entreprise';
import { identifiantDeVente } from '../noyau/identifiants';
import { nouveauCodeDeTicket } from '../noyau/code-ticket';
import { imprimerLeTicket } from '../noyau/impression';
import { PaiementDuTicket, Ticket } from '../ticket/ticket';
import { MODES_DE_REGLEMENT } from '../noyau/reglements';
import type { ArticleDto, ClientDto, EntrepriseDto } from '../noyau/api';

/** Les montants suivent la regle du serveur : deux decimales, au plus pres. */
function arrondi(montant: number): number {
  return Math.round(montant * 100) / 100;
}

/** Ce qu'on tend au comptoir : les coupures qui evitent de compter la monnaie a l'unite. */
const COUPURES = [500, 1000, 2000, 5000, 10000];

/** Un article dans le panier, avec la quantite que le caissier a saisie. */
interface LignePanier {
  article: ArticleDto;
  quantite: number;
}

/**
 * Le panier tel qu'il etait au moment de valider.
 *
 * Une vente qui bascule hors ligne en cours de route — la vente est passee, la facture non —
 * doit etre gardee telle que le client l'a payee, et non telle que l'ecran l'affiche une seconde
 * plus tard.
 */
interface Instantane {
  lignes: LignePanier[];
  client: ClientDto | null;
  totalHt: number;
  totalTva: number;
  totalTtc: number;
}

/**
 * La vente au comptoir : chercher, ajouter, vendre.
 *
 * Le panier se construit article par article, parce que c'est ainsi qu'il se presente : on ne
 * connait pas ce qu'un client achete avant qu'il ait pose son dernier article. Rien ne part au
 * serveur avant le bouton final — une vente a moitie enregistree serait pire que pas de vente.
 *
 * <b>Sans reseau.</b> Le comptoir ne s'arrete pas quand le serveur ne repond plus. Les articles se
 * trouvent dans une copie du catalogue gardee sur l'appareil, et la vente est gardee elle aussi,
 * avec ce que le client a paye, jusqu'a ce qu'elle parte (`FileDesVentes`). Le client repart avec
 * un ticket provisoire ; la facture est emise quand la vente arrive au serveur.
 *
 * Une vente qui perd le reseau en cours de route — la vente est passee, la facture non, ou la
 * facture est emise et l'encaissement non — bascule de la meme facon. C'est sans risque : elle
 * porte la reference tiree ici, et le serveur reprend ce qui est deja fait au lieu de le refaire.
 *
 * <b>La douchette.</b> Un lecteur de code-barres USB est un clavier : il tape le code dans le
 * champ qui a le point, puis envoie Entree. Il n'y a donc rien a brancher — ni camera, ni
 * bibliotheque, ni permission du navigateur. Tout tient dans ce que fait Entree : chercher le
 * code exact, et poser l'article au panier.
 *
 * Le meme champ sert a chercher par le nom, et c'est voulu. Un champ dedie au scan, toujours au
 * point, volerait le curseur au caissier des qu'il veut taper autre chose ; deux champs cote a
 * cote l'obligeraient a choisir avant chaque geste. Celui-ci ne demande rien : on scanne, ou on
 * tape, et Entree ne se trompe pas puisqu'elle ne repond qu'a un code exact.
 */
@Component({
  selector: 'app-vente-au-comptoir',
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    Ticket,
  ],
  templateUrl: './vente-au-comptoir.html',
})
export class VenteAuComptoir {
  private readonly service = inject(Comptoir);
  private readonly magasinService = inject(Entreprise);
  private readonly snack = inject(MatSnackBar);
  private readonly reseau = inject(Reseau);
  private readonly catalogueLocal = inject(CatalogueLocal);
  private readonly file = inject(FileDesVentes);
  private readonly frappe = new Subject<string>();
  private readonly frappeClient = new Subject<string>();

  /** Le serveur repond-il ? Faux : on vend avec ce que l'appareil a garde. */
  protected readonly joignable = this.reseau.joignable;
  /** Le nombre d'articles gardes sur l'appareil, et de quand ils datent. */
  protected readonly articlesGardes = this.catalogueLocal.taille;
  protected readonly dateDuCatalogue = this.catalogueLocal.date;

  /**
   * Le prix hors taxes auquel l'article se vend aujourd'hui : celui de sa campagne, s'il est en
   * promotion. C'est ce prix qui part au serveur, qui s'imprime hors ligne, et qu'on annonce.
   */
  protected prix(article: ArticleDto): number {
    return this.catalogueLocal.prixDe(article);
  }

  /** La promotion du jour de l'article, pour barrer le prix normal a cote du prix reduit. */
  protected promotion(article: ArticleDto) {
    return this.catalogueLocal.promotionDe(article);
  }
  /** Les ventes faites ici et pas encore parties. */
  protected readonly enAttente = computed(() => this.file.aEnvoyer().length);

  protected readonly recherche = signal('');
  protected readonly resultats = signal<ArticleDto[]>([]);
  protected readonly cherche = signal(false);

  protected readonly panier = signal<LignePanier[]>([]);
  protected readonly client = signal<ClientDto | null>(null);
  protected readonly rechercheClient = signal('');
  protected readonly clientsTrouves = signal<ClientDto[]>([]);
  protected readonly choixDuClient = signal(false);

  protected readonly envoiEnCours = signal(false);
  protected readonly erreur = signal<string | null>(null);

  /** Le champ de recherche, qu'on rend au caissier apres chaque geste : le scan suivant y va. */
  private readonly champRecherche = viewChild<ElementRef<HTMLInputElement>>('champRecherche');

  /** Resolution d'un code en cours : deux scans coup sur coup ne doivent pas se chevaucher. */
  protected readonly resolution = signal(false);

  /**
   * Le code scanne qu'aucun article ne porte.
   *
   * Dit sans bloquer : au comptoir, on ne perd pas un panier parce qu'un article manque au
   * catalogue. Le caissier passe au suivant et regularisera plus tard.
   */
  protected readonly codeInconnu = signal<string | null>(null);

  /**
   * Le dernier article pose au panier, le temps d'un clignotement.
   *
   * Le caissier ne regarde pas l'ecran quand il scanne — il tient la marchandise. Sans ce retour,
   * rien ne distingue un scan qui a porte d'un scan que la douchette a manque, et on s'en aperçoit
   * au total.
   */
  protected readonly dernierAjout = signal<number | null>(null);
  private clignotement?: ReturnType<typeof setTimeout>;

  /**
   * L'identite du magasin : l'en-tete du ticket, et le regime de TVA qui decide du total a
   * encaisser. Chargee des l'ouverture du comptoir, pas au moment ou le client attend.
   */
  protected readonly magasin = signal<EntrepriseDto | null>(null);

  /** Le panneau d'encaissement, une fois le panier ferme. */
  protected readonly encaissement = signal(false);
  protected readonly mode = signal<ModeReglement>('ESPECES');
  /** Ce que le client tend. Nul veut dire « le compte exact ». */
  protected readonly recu = signal<number | null>(null);
  protected readonly modes = MODES_DE_REGLEMENT;

  /**
   * Le bon d'achat que tend le client, une fois lu. Il vient en deduction du total avant tout
   * autre moyen : le client ne paie que ce qui reste. Il ne se lit qu'en ligne — hors reseau, rien
   * ne dirait s'il a deja servi.
   */
  protected readonly saisieDuBon = signal(false);
  protected readonly codeDuBon = signal('');
  protected readonly bon = signal<BonDAchatDto | null>(null);
  protected readonly lectureDuBon = signal(false);
  protected readonly erreurDuBon = signal<string | null>(null);
  protected readonly enLigne = computed(() => this.reseau.joignable());
  protected readonly coupures = COUPURES;

  /** Le ticket a imprimer : pose apres l'encaissement, retire quand le caissier le referme. */
  protected readonly aImprimer = signal<{
    facture: FactureDto;
    paiement: PaiementDuTicket;
    /** Vendu hors ligne : sans numero, la facture suivra. */
    provisoire: boolean;
    /** Vendu pendant une campagne : le ticket annonce ses points. */
    enCampagne: boolean;
  } | null>(null);
  private readonly zoneTicket = viewChild<ElementRef<HTMLElement>>('zoneTicket');

  protected readonly total = computed(() =>
    this.panier().reduce(
      (somme, l) => somme + arrondi(this.prix(l.article) * l.quantite),
      0,
    ),
  );
  protected readonly articles = computed(() => this.panier().length);

  /**
   * La TVA du panier, estimee ici avec la regle du serveur : l'entreprise decide d'abord — non
   * assujettie, rien n'est taxe — puis le taux de l'article l'emporte s'il en porte un, et a
   * defaut celui de l'entreprise s'applique.
   *
   * C'est une estimation, et elle est assumee : le caissier doit annoncer un montant avant que
   * quoi que ce soit ne parte au serveur, sans quoi il prendrait l'argent apres avoir enregistre
   * la vente. Ce qui sera reellement encaisse et imprime, lui, vient de la facture emise — les
   * deux ne peuvent differer que d'un franc d'arrondi, et c'est le papier qui fait foi.
   */
  protected readonly totalTva = computed(() =>
    this.panier().reduce((somme, l) => {
      const ht = arrondi(this.prix(l.article) * l.quantite);
      return somme + arrondi((ht * this.taux(l.article)) / 100);
    }, 0),
  );
  protected readonly totalTtc = computed(() => arrondi(this.total() + this.totalTva()));

  /**
   * Ce que le bon retire du total. Au plus le total : un bon plus gros que l'achat ne rend pas la
   * monnaie, et le caissier doit le dire au client avant de valider.
   */
  protected readonly deductionDuBon = computed(() =>
    Math.min(this.bon()?.montantFcfa ?? 0, this.totalTtc()),
  );
  /** Ce que le bon vaut au-dela de l'achat, et que le client perd. */
  protected readonly bonPerdu = computed(() =>
    Math.max(0, arrondi((this.bon()?.montantFcfa ?? 0) - this.totalTtc())),
  );
  /** Ce qui reste a payer une fois le bon deduit : c'est ce chiffre qu'on annonce. */
  protected readonly aPayer = computed(() => arrondi(this.totalTtc() - this.deductionDuBon()));

  /** Ce qu'on rend. Zero tant que le client n'a pas tendu plus que ce qu'il doit. */
  protected readonly aRendre = computed(() =>
    Math.max(0, arrondi((this.recu() ?? this.aPayer()) - this.aPayer())),
  );
  /** Ce qui manquerait si l'on validait maintenant : un acompte, et le reste reste du. */
  protected readonly manquant = computed(() =>
    Math.max(0, arrondi(this.aPayer() - (this.recu() ?? this.aPayer()))),
  );

  /** Les coupures superieures a ce qui reste a payer : ce qu'un client est susceptible de tendre. */
  protected readonly coupuresUtiles = computed(() => {
    const total = this.aPayer();
    return COUPURES.filter((c) => c > total).slice(0, 3);
  });

  private taux(article: ArticleDto): number {
    const magasin = this.magasin();
    if (magasin?.assujettieTva === false) {
      return 0;
    }
    if (article.tauxTva != null) {
      return article.tauxTva;
    }
    return magasin?.tauxTva ?? 0;
  }

  constructor() {
    // L'en-tete du magasin et son regime de TVA, des l'ouverture du comptoir : quand le client
    // attend son ticket, il est trop tard pour aller les chercher.
    this.magasinService
      .charger()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (magasin) => this.magasin.set(magasin),
        // Sans identite, on vend quand meme : le ticket sortira sans en-tete plutot que pas du
        // tout. Perdre une vente parce qu'un nom de magasin manque serait absurde.
        error: () => this.magasin.set(null),
      });

    // La copie du catalogue : relue de l'appareil tout de suite, recopiee du serveur des qu'il
    // repond, puis toutes les dix minutes tant que le comptoir est ouvert.
    void this.catalogueLocal.charger();
    effect(() => {
      if (this.reseau.joignable()) {
        untracked(() => this.rafraichirLeCatalogue());
      }
    });
    interval(60_000)
      .pipe(takeUntilDestroyed())
      .subscribe(() => {
        if (this.reseau.joignable()) {
          this.rafraichirLeCatalogue();
        }
      });

    this.frappe
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((q) => this.articlesPour(q)),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (articles) => {
          this.cherche.set(false);
          // Une recherche partie pendant la frappe repond apres que le scan a pose l'article et
          // vide le champ. Sans ce garde, la tuile du produit deja au panier restait affichee,
          // et l'ecran avait l'air de n'avoir rien fait.
          if (!this.recherche().trim()) {
            return;
          }
          this.resultats.set(articles);
        },
        error: () => this.cherche.set(false),
      });

    this.frappeClient
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((q) => this.service.clients(q)),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (page) => this.clientsTrouves.set(page.content ?? []),
        error: () => this.clientsTrouves.set([]),
      });
  }

  private rafraichirLeCatalogue(): void {
    // Un echec ici ne gene personne : on garde la copie d'avant, et l'on retentera.
    this.catalogueLocal.rafraichirSiAncien().catch(() => undefined);
  }

  /**
   * Les articles qui repondent a une recherche : ceux du serveur s'il repond, sinon ceux de la
   * copie gardee sur l'appareil.
   *
   * Le serveur d'abord, tant qu'il est la : il a les prix du moment. Mais la bascule se fait des
   * la premiere coupure constatee, sans attendre l'expiration de chaque recherche — vingt
   * secondes par frappe rendraient le comptoir inutilisable.
   */
  private articlesPour(q: string): Observable<ArticleDto[]> {
    if (!this.reseau.joignable()) {
      return of(this.catalogueLocal.chercher(q));
    }
    return this.service.articles(q).pipe(
      map((page) => page.content ?? []),
      catchError((echec: unknown) =>
        estUneCoupure(echec) ? of(this.catalogueLocal.chercher(q)) : throwError(() => echec),
      ),
    );
  }

  protected chercher(q: string): void {
    this.recherche.set(q);
    // Retaper efface le refus precedent : le garder ferait croire que le nouveau code est
    // inconnu lui aussi.
    this.codeInconnu.set(null);
    if (!q.trim()) {
      this.resultats.set([]);
      return;
    }
    this.cherche.set(true);
    this.frappe.next(q);
  }

  /**
   * Entree : le code exact, et rien d'autre.
   *
   * Strictement le code, meme quand la liste ci-dessous ne montre qu'un seul resultat. Ajouter
   * « le seul article affiche » serait juste la plupart du temps, et faux le jour ou la recherche
   * est en retard d'une frappe — au comptoir, devant un client, une vente fausse coute plus cher
   * qu'un clic de plus.
   */
  protected valider(): void {
    const saisi = this.recherche().trim();
    if (!saisi || this.resolution()) {
      return;
    }
    this.resolution.set(true);
    this.codeInconnu.set(null);

    if (!this.reseau.joignable()) {
      this.resoudreSurLAppareil(saisi);
      return;
    }
    this.service.parCode(saisi).subscribe({
      next: (article) => {
        this.resolution.set(false);
        this.ajouter(article);
      },
      error: (echec: unknown) => {
        if (estUneCoupure(echec)) {
          this.resoudreSurLAppareil(saisi);
          return;
        }
        this.codeRefuse(saisi);
      },
    });
  }

  /** Le code cherche dans la copie du catalogue, quand le serveur ne repond pas. */
  private resoudreSurLAppareil(code: string): void {
    const article = this.catalogueLocal.parCode(code);
    if (article) {
      this.resolution.set(false);
      this.ajouter(article);
    } else {
      this.codeRefuse(code);
    }
  }

  private codeRefuse(code: string): void {
    this.resolution.set(false);
    // La saisie reste : elle sert de point de depart a une recherche par le nom.
    this.codeInconnu.set(code);
    this.rendreLePoint();
  }

  /** Remet le point au champ, pour que le scan suivant parte sans un clic. */
  private rendreLePoint(): void {
    this.champRecherche()?.nativeElement.focus();
  }

  /**
   * Ajoute un article, ou incremente celui qui est deja la.
   *
   * Passer deux fois le meme article au comptoir veut dire « deux unites », pas « deux lignes » :
   * le ticket serait illisible et le total identique.
   */
  protected ajouter(article: ArticleDto): void {
    this.panier.update((liste) => {
      const existante = liste.find((l) => l.article.id === article.id);
      return existante
        ? liste.map((l) => (l.article.id === article.id ? { ...l, quantite: l.quantite + 1 } : l))
        : [...liste, { article, quantite: 1 }];
    });
    this.recherche.set('');
    this.resultats.set([]);
    this.codeInconnu.set(null);
    // Le squelette de chargement de la recherche en vol n'a plus lieu d'etre : l'article est pose.
    this.cherche.set(false);

    // Le clignotement de la ligne, et le point rendu au champ : le caissier enchaine les scans
    // sans jamais toucher la souris.
    this.dernierAjout.set(article.id ?? null);
    clearTimeout(this.clignotement);
    this.clignotement = setTimeout(() => this.dernierAjout.set(null), 900);
    this.rendreLePoint();
  }

  protected changerQuantite(idArticle: number, quantite: number): void {
    if (quantite <= 0) {
      this.retirer(idArticle);
      return;
    }
    this.panier.update((liste) =>
      liste.map((l) => (l.article.id === idArticle ? { ...l, quantite } : l)),
    );
  }

  protected retirer(idArticle: number): void {
    this.panier.update((liste) => liste.filter((l) => l.article.id !== idArticle));
  }

  protected viderLePanier(): void {
    this.panier.set([]);
    this.client.set(null);
    this.erreur.set(null);
  }

  protected chercherClient(q: string): void {
    this.rechercheClient.set(q);
    if (!q.trim()) {
      this.clientsTrouves.set([]);
      return;
    }
    this.frappeClient.next(q);
  }

  protected choisirClient(c: ClientDto | null): void {
    this.client.set(c);
    this.choixDuClient.set(false);
    this.rechercheClient.set('');
    this.clientsTrouves.set([]);
  }

  protected nomDuClient(c: ClientDto): string {
    return [c.nom, c.prenoms].filter(Boolean).join(' ');
  }

  /**
   * Ouvre l'encaissement : on ferme le panier et l'on passe a l'argent.
   *
   * Le total annonce est celui du panier, TVA comprise — c'est ce que le client paie, et ce que
   * le caissier doit pouvoir dire a voix haute avant que rien ne soit enregistre.
   */
  protected ouvrirEncaissement(): void {
    if (this.panier().length === 0 || this.envoiEnCours()) {
      return;
    }
    this.erreur.set(null);
    this.recu.set(null);
    this.mode.set('ESPECES');
    this.oublierLeBon();
    this.encaissement.set(true);
  }

  /** Lit le bon tendu par le client : son montant, et s'il vaut encore dans ce magasin. */
  protected lireLeBon(): void {
    const code = this.codeDuBon().trim();
    if (!code || this.lectureDuBon()) {
      return;
    }
    this.lectureDuBon.set(true);
    this.erreurDuBon.set(null);
    this.service.verifierBon(code).subscribe({
      next: (bon) => {
        this.lectureDuBon.set(false);
        if (!bon.utilisable) {
          this.erreurDuBon.set(
            bon.statut === 'EXPIRE' ? 'Ce bon a expiré.' : 'Ce bon a déjà été utilisé.',
          );
          return;
        }
        this.bon.set(bon);
        // Le montant tendu se recompte sur ce qui reste a payer.
        this.recu.set(null);
      },
      error: (echec: unknown) => {
        this.lectureDuBon.set(false);
        this.erreurDuBon.set(messageDErreur(echec, 'Ce bon n’a pas pu être lu.'));
      },
    });
  }

  protected oublierLeBon(): void {
    this.bon.set(null);
    this.codeDuBon.set('');
    this.erreurDuBon.set(null);
    this.saisieDuBon.set(false);
    this.recu.set(null);
  }

  protected fermerEncaissement(): void {
    this.encaissement.set(false);
    this.rendreLePoint();
  }

  protected choisirLeMode(mode: ModeReglement): void {
    this.mode.set(mode);
    // Hors especes, il n'y a pas de monnaie a rendre : le montant tendu est le compte exact.
    if (mode !== 'ESPECES') {
      this.recu.set(null);
    }
  }

  protected proposer(montant: number | null): void {
    this.recu.set(montant);
  }

  /**
   * Enregistre la vente, emet sa facture, encaisse, puis imprime.
   *
   * Quatre appels et non un, et leur ordre porte tout le raisonnement :
   *
   * La vente d'abord, parce que la marchandise est partie — c'est le fait a constater, et il ne
   * doit dependre de rien d'autre. Si la facture echoue ensuite, la vente reste : l'effacer pour
   * un probleme de facturation serait perdre la plus importante des deux informations.
   *
   * La facture ensuite, qui fige les prix et les taux et donne le seul total qui fasse foi. C'est
   * le sien, et non l'estimation du panier, qui est encaisse et imprime.
   *
   * L'encaissement enfin. S'il echoue, la vente et la facture existent toujours : le ticket sort
   * quand meme, avec son reste a payer, et la facture se retrouve dans « a encaisser ». Rien n'est
   * perdu, et le caissier le sait.
   *
   * Une coupure, a n'importe laquelle des trois etapes, fait basculer la vente hors ligne avec ce
   * que le client a paye (`garderHorsLigne`). Le serveur reprendra la ou il en etait.
   */
  protected encaisser(): void {
    if (this.envoiEnCours() || this.panier().length === 0) {
      return;
    }
    this.envoiEnCours.set(true);
    this.erreur.set(null);

    const instantane: Instantane = {
      lignes: this.panier(),
      client: this.client(),
      totalHt: this.total(),
      totalTva: this.totalTva(),
      totalTtc: this.totalTtc(),
    };
    const vente: VenteDto = {
      code: `V-${Date.now()}`,
      // Rejouable sans risque : reposter la meme reference rend la vente deja enregistree au lieu
      // d'en creer une seconde. Le comptoir aussi peut perdre sa reponse.
      //
      // `identifiantDeVente` et non `crypto.randomUUID` : cette derniere n'existe qu'en contexte
      // securise, et le magasin sert l'application en clair sur son reseau local.
      referenceClient: identifiantDeVente(),
      // Le code du QR imprime sur le ticket, tire ici pour qu'un ticket hors ligne porte deja le
      // sien : le serveur le garde tel quel.
      codeTicket: nouveauCodeDeTicket(),
      client: this.client() ?? undefined,
      ligneVente: this.panier().map((l) => ({
        article: { id: l.article.id },
        quantite: l.quantite,
        prixUnitaire: this.prix(l.article),
      })),
    };
    const bon = this.bon();
    // Avec un bon, le client ne tend que ce qui reste : c'est ce qui part si la vente bascule hors
    // ligne, le bon n'ayant pas pu etre encaisse.
    const tendu = this.recu() ?? (bon ? this.aPayer() : null);
    const mode = this.mode();
    const deduction = this.deductionDuBon();
    const horsLigne = () => {
      this.garderHorsLigne(vente, instantane, tendu, mode);
      if (bon) {
        this.snack.open(
          `Le bon ${bon.codeBon} n’a pas pu être encaissé : la facture garde ${deduction.toLocaleString()} F à payer. Encaissez-le depuis les factures au retour du réseau.`,
          'Fermer',
          { duration: 12000 },
        );
      }
    };

    // Le serveur ne repondait deja plus : inutile de lui laisser vingt secondes pour le redire,
    // pendant que le client attend.
    if (!this.reseau.joignable()) {
      if (bon) {
        // Un bon ne se verifie qu'en ligne : sans reseau, rien ne dit qu'il n'a pas deja servi.
        this.envoiEnCours.set(false);
        this.erreur.set(
          'Le réseau est coupé : le bon ne peut pas être encaissé. Retirez-le pour vendre hors ligne.',
        );
        return;
      }
      horsLigne();
      return;
    }

    this.service.vendre(vente).subscribe({
      next: (enregistree) => {
        this.service.facturer(enregistree.id!).subscribe({
          next: (facture) =>
            bon
              ? this.encaisserLeBon(facture, bon, tendu, mode, vente)
              : this.encaisserLaFacture(facture, tendu, mode, vente),
          error: (echec: unknown) => {
            if (estUneCoupure(echec)) {
              // La vente est passee, la facture non : la synchronisation l'emettra et encaissera,
              // en retrouvant la vente par sa reference.
              horsLigne();
              return;
            }
            // La vente est passee : c'est ce qui compte, la marchandise est partie.
            this.terminer();
            this.snack.open(
              'Vente enregistrée, mais la facture n’a pas pu être émise — rien n’est imprimé.',
              'Fermer',
              { duration: 8000 },
            );
          },
        });
      },
      error: (echec: unknown) => {
        if (estUneCoupure(echec)) {
          // Peut-etre passee, peut-etre pas : on ne le saura qu'a la synchronisation, et c'est
          // sans risque — la reference fera retrouver la vente au lieu d'en creer une seconde.
          horsLigne();
          return;
        }
        this.envoiEnCours.set(false);
        // Le message de l'API dit quel article manque et combien il en reste : le remplacer par
        // un texte generique effacerait la seule information utile au comptoir.
        this.erreur.set(messageDErreur(echec, 'La vente n’a pas pu être enregistrée.'));
      },
    });
  }

  /**
   * Garde la vente sur l'appareil, avec ce que le client a paye, et sort un ticket provisoire.
   *
   * Le ticket ne sort qu'une fois la vente ecrite sur le disque : si l'appareil ne peut pas la
   * garder, le caissier doit le savoir avant de rendre la monnaie, pas apres.
   *
   * Ce qui est encaisse part tel que le client l'a tendu : le serveur le plafonne au total de la
   * facture qu'il emettra, le surplus etant la monnaie rendue.
   */
  private garderHorsLigne(
    vente: VenteDto,
    instantane: Instantane,
    tendu: number | null,
    mode: ModeReglement,
  ): void {
    const du = instantane.totalTtc;
    const aEnvoyer: VenteSynchronisee = {
      ...vente,
      // L'heure de la vente, celle du tiroir. Si la vente etait deja passee avant la coupure, le
      // serveur garde la sienne.
      datevente: new Date().toISOString(),
      encaissement: { montant: tendu ?? du, mode },
    };

    this.file.garder(aEnvoyer, du).then(
      () => {
        const encaisse = Math.min(tendu ?? du, du);
        const paiement: PaiementDuTicket = {
          mode,
          recu: tendu ?? du,
          monnaie: Math.max(0, arrondi((tendu ?? du) - du)),
        };
        this.presenterLeTicket(
          { ...this.factureProvisoire(instantane, encaisse), codeTicket: vente.codeTicket },
          paiement,
          paiement.monnaie > 0
            ? `Hors ligne — vente gardée sur l’appareil. Rendre ${paiement.monnaie.toLocaleString()} F`
            : 'Hors ligne — vente gardée sur l’appareil, elle partira au retour du réseau.',
          true,
        );
      },
      () => {
        this.envoiEnCours.set(false);
        this.erreur.set(
          'Le serveur ne répond pas, et cet appareil n’a pas pu garder la vente : elle n’est pas enregistrée.',
        );
      },
    );
  }

  /**
   * Le ticket d'une vente faite hors ligne, calcule au comptoir.
   *
   * Avec les memes regles que la facture du serveur — prix de la ligne, taux de l'article ou a
   * defaut du magasin, arrondi par ligne — pour que le papier du client et la facture qui suivra
   * disent le meme montant.
   */
  private factureProvisoire(instantane: Instantane, encaisse: number): FactureDto {
    const magasin = this.magasin();
    return {
      dateEmission: new Date().toISOString(),
      nomClient: instantane.client ? this.nomDuClient(instantane.client) : undefined,
      // Meme regle que le serveur : sans magasin connu, la TVA s'applique.
      tvaApplicable: magasin?.assujettieTva !== false,
      lignes: instantane.lignes.map((l, rang) => {
        const prix = this.prix(l.article);
        const taux = this.taux(l.article);
        const ht = arrondi(prix * l.quantite);
        const tva = arrondi((ht * taux) / 100);
        return {
          id: rang,
          codeArticle: l.article.codeArticle,
          designation: l.article.designation,
          quantite: l.quantite,
          prixUnitaireHt: prix,
          tauxTva: taux,
          montantHt: ht,
          montantTva: tva,
          montantTtc: arrondi(ht + tva),
        };
      }),
      totalHt: instantane.totalHt,
      totalTva: instantane.totalTva,
      totalTtc: instantane.totalTtc,
      montantRegle: encaisse,
      resteAPayer: arrondi(instantane.totalTtc - encaisse),
    };
  }

  /**
   * Encaisse d'abord le bon, puis le reste par le moyen choisi.
   *
   * Le montant que le bon regle est celui que rend le serveur — le bon plafonne au reste a payer
   * de la facture, qui fait foi, et non a l'estimation du panier.
   */
  private encaisserLeBon(
    facture: FactureDto,
    bon: BonDAchatDto,
    tendu: number | null,
    mode: ModeReglement,
    vente: VenteDto,
  ): void {
    this.service.regler(facture.id!, { mode: 'BON_ACHAT', reference: bon.codeBon }).subscribe({
      next: (reglement) => {
        this.encaisserLaFacture(facture, tendu, mode, vente, {
          code: bon.codeBon,
          montant: reglement.montant ?? 0,
        });
      },
      error: (echec: unknown) => {
        // La vente et sa facture existent : le ticket sort avec son reste a payer, et la facture
        // attend dans « a encaisser », ou le bon pourra etre repris.
        this.presenterLeTicket({ ...facture }, { mode, recu: 0, monnaie: 0 }, null);
        this.erreur.set(
          messageDErreur(echec, 'Le bon n’a pas pu être encaissé : la facture reste due.'),
        );
      },
    });
  }

  private encaisserLaFacture(
    facture: FactureDto,
    tendu: number | null,
    mode: ModeReglement,
    vente: VenteDto,
    bon?: { code: string; montant: number },
  ): void {
    const parBon = bon?.montant ?? 0;
    const du = arrondi((facture.totalTtc ?? 0) - parBon);
    // Ce qui entre en caisse est plafonne a ce qui est du : le surplus n'est pas une recette,
    // c'est la monnaie qu'on rend. Le serveur refuserait d'ailleurs un montant qui depasse.
    const encaisse = Math.min(tendu ?? du, du);
    const paiement: PaiementDuTicket = {
      mode,
      recu: du > 0 ? (tendu ?? du) : 0,
      monnaie: Math.max(0, arrondi((tendu ?? du) - du)),
      bon,
    };

    if (encaisse <= 0) {
      this.presenterLeTicket(
        { ...facture, montantRegle: parBon, resteAPayer: du },
        paiement,
        bon && du <= 0 ? 'Réglé par le bon d’achat.' : 'Vente enregistrée — rien n’a été encaissé.',
      );
      return;
    }

    this.service.regler(facture.id!, { montant: encaisse, mode }).subscribe({
      next: () => {
        // Ce qui reste du se deduit sans rien redemander au serveur : il vient d'accepter ce
        // montant, donc de constater qu'il ne depassait pas. Un aller-retour de plus ferait
        // attendre un client qui a deja paye.
        this.presenterLeTicket(
          { ...facture, montantRegle: parBon + encaisse, resteAPayer: arrondi(du - encaisse) },
          paiement,
          paiement.monnaie > 0
            ? `Encaissé — rendre ${paiement.monnaie.toLocaleString()} F`
            : 'Encaissé.',
        );
      },
      error: (echec: unknown) => {
        if (estUneCoupure(echec)) {
          // La facture existe, l'encaissement est peut-etre passe. Il part avec la vente : le
          // serveur n'encaisse pas une facture qui porte deja un reglement, et date celui-ci de
          // la vente. Le client, lui, a paye : son ticket le dit.
          const aEnvoyer: VenteSynchronisee = {
            ...vente,
            datevente: new Date().toISOString(),
            encaissement: { montant: tendu ?? du, mode },
          };
          this.file.garder(aEnvoyer, du).then(
            () =>
              this.presenterLeTicket(
                { ...facture, montantRegle: parBon + encaisse, resteAPayer: arrondi(du - encaisse) },
                paiement,
                'Hors ligne — l’encaissement est gardé sur l’appareil, il partira au retour du réseau.',
              ),
            () => {
              this.presenterLeTicket({ ...facture }, { ...paiement, recu: 0, monnaie: 0 }, null);
              this.erreur.set(
                'L’encaissement n’a pas pu être enregistré : la facture reste due.',
              );
            },
          );
          return;
        }
        // La vente et la facture existent : le ticket sort avec son reste a payer, et la facture
        // attend dans « a encaisser ».
        this.presenterLeTicket({ ...facture }, { ...paiement, recu: 0, monnaie: 0 }, null);
        this.erreur.set(
          messageDErreur(echec, 'L’encaissement n’a pas pu être enregistré : la facture reste due.'),
        );
      },
    });
  }

  /** Pose le ticket a l'ecran, vide le panier, et rend le comptoir pret pour le client suivant. */
  private presenterLeTicket(
    facture: FactureDto,
    paiement: PaiementDuTicket,
    message: string | null,
    provisoire = false,
  ): void {
    this.aImprimer.set({ facture, paiement, provisoire, enCampagne: this.catalogueLocal.enCampagne() });
    this.terminer();
    if (message) {
      this.snack.open(message, 'Fermer', { duration: 5000 });
    }
  }

  private terminer(): void {
    this.envoiEnCours.set(false);
    this.encaissement.set(false);
    this.viderLePanier();
  }

  /** Envoie le ticket affiche sur le rouleau. */
  protected imprimer(): void {
    const zone = this.zoneTicket()?.nativeElement;
    if (zone) {
      imprimerLeTicket(zone);
    }
  }

  protected fermerLeTicket(): void {
    this.aImprimer.set(null);
    this.rendreLePoint();
  }
}
