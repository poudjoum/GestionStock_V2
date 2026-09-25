import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Comptoir, FactureDto, ModeReglement, VenteDto } from './comptoir.service';
import { messageDErreur } from '../noyau/erreurs';
import { Entreprise } from '../noyau/entreprise';
import { identifiantDeVente } from '../noyau/identifiants';
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
 * La vente au comptoir : chercher, ajouter, vendre.
 *
 * Le panier se construit article par article, parce que c'est ainsi qu'il se presente : on ne
 * connait pas ce qu'un client achete avant qu'il ait pose son dernier article. Rien ne part au
 * serveur avant le bouton final — une vente a moitie enregistree serait pire que pas de vente.
 *
 * Le code de la vente est tire ici. Il pourrait l'etre par le serveur, mais le tirer localement
 * est ce qui permettra, au lot du hors-ligne, de poser une vente dans une file sans reseau.
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
  private readonly frappe = new Subject<string>();
  private readonly frappeClient = new Subject<string>();

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
  protected readonly coupures = COUPURES;

  /** Le ticket a imprimer : pose apres l'encaissement, retire quand le caissier le referme. */
  protected readonly aImprimer = signal<{
    facture: FactureDto;
    paiement: PaiementDuTicket;
  } | null>(null);
  private readonly zoneTicket = viewChild<ElementRef<HTMLElement>>('zoneTicket');

  protected readonly total = computed(() =>
    this.panier().reduce(
      (somme, l) => somme + arrondi((l.article.prixUnitaireHt ?? 0) * l.quantite),
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
      const ht = arrondi((l.article.prixUnitaireHt ?? 0) * l.quantite);
      return somme + arrondi((ht * this.taux(l.article)) / 100);
    }, 0),
  );
  protected readonly totalTtc = computed(() => arrondi(this.total() + this.totalTva()));

  /** Ce qu'on rend. Zero tant que le client n'a pas tendu plus que le total. */
  protected readonly aRendre = computed(() =>
    Math.max(0, arrondi((this.recu() ?? this.totalTtc()) - this.totalTtc())),
  );
  /** Ce qui manquerait si l'on validait maintenant : un acompte, et le reste reste du. */
  protected readonly manquant = computed(() =>
    Math.max(0, arrondi(this.totalTtc() - (this.recu() ?? this.totalTtc()))),
  );

  /** Les coupures superieures au total : ce qu'un client est susceptible de tendre. */
  protected readonly coupuresUtiles = computed(() => {
    const total = this.totalTtc();
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

    this.frappe
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((q) => this.service.articles(q)),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (page) => {
          this.cherche.set(false);
          // Une recherche partie pendant la frappe repond apres que le scan a pose l'article et
          // vide le champ. Sans ce garde, la tuile du produit deja au panier restait affichee,
          // et l'ecran avait l'air de n'avoir rien fait.
          if (!this.recherche().trim()) {
            return;
          }
          this.resultats.set(page.content ?? []);
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

    this.service.parCode(saisi).subscribe({
      next: (article) => {
        this.resolution.set(false);
        this.ajouter(article);
      },
      error: () => {
        this.resolution.set(false);
        // La saisie reste : elle sert de point de depart a une recherche par le nom.
        this.codeInconnu.set(saisi);
        this.rendreLePoint();
      },
    });
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
    this.encaissement.set(true);
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
   */
  protected encaisser(): void {
    if (this.envoiEnCours() || this.panier().length === 0) {
      return;
    }
    this.envoiEnCours.set(true);
    this.erreur.set(null);

    const vente: VenteDto = {
      code: `V-${Date.now()}`,
      // Rejouable sans risque : reposter la meme reference rend la vente deja enregistree au lieu
      // d'en creer une seconde. Le comptoir aussi peut perdre sa reponse.
      //
      // `identifiantDeVente` et non `crypto.randomUUID` : cette derniere n'existe qu'en contexte
      // securise, et le magasin sert l'application en clair sur son reseau local.
      referenceClient: identifiantDeVente(),
      client: this.client() ?? undefined,
      ligneVente: this.panier().map((l) => ({
        article: { id: l.article.id },
        quantite: l.quantite,
        prixUnitaire: l.article.prixUnitaireHt,
      })),
    };
    const tendu = this.recu();
    const mode = this.mode();

    this.service.vendre(vente).subscribe({
      next: (enregistree) => {
        this.service.facturer(enregistree.id!).subscribe({
          next: (facture) => this.encaisserLaFacture(facture, tendu, mode),
          error: () => {
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
        this.envoiEnCours.set(false);
        // Le message de l'API dit quel article manque et combien il en reste : le remplacer par
        // un texte generique effacerait la seule information utile au comptoir.
        this.erreur.set(messageDErreur(echec, 'La vente n’a pas pu être enregistrée.'));
      },
    });
  }

  private encaisserLaFacture(
    facture: FactureDto,
    tendu: number | null,
    mode: ModeReglement,
  ): void {
    const du = facture.totalTtc ?? 0;
    // Ce qui entre en caisse est plafonne a ce qui est du : le surplus n'est pas une recette,
    // c'est la monnaie qu'on rend. Le serveur refuserait d'ailleurs un montant qui depasse.
    const encaisse = Math.min(tendu ?? du, du);
    const paiement: PaiementDuTicket = {
      mode,
      recu: tendu ?? du,
      monnaie: Math.max(0, arrondi((tendu ?? du) - du)),
    };

    if (encaisse <= 0) {
      this.presenterLeTicket(facture, paiement, 'Vente enregistrée — rien n’a été encaissé.');
      return;
    }

    this.service.regler(facture.id!, { montant: encaisse, mode }).subscribe({
      next: () => {
        // Ce qui reste du se deduit sans rien redemander au serveur : il vient d'accepter ce
        // montant, donc de constater qu'il ne depassait pas. Un aller-retour de plus ferait
        // attendre un client qui a deja paye.
        this.presenterLeTicket(
          { ...facture, montantRegle: encaisse, resteAPayer: arrondi(du - encaisse) },
          paiement,
          paiement.monnaie > 0
            ? `Encaissé — rendre ${paiement.monnaie.toLocaleString()} F`
            : 'Encaissé.',
        );
      },
      error: (echec: unknown) => {
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
  ): void {
    this.aImprimer.set({ facture, paiement });
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
