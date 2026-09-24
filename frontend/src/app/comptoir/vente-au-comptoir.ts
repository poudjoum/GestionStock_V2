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
import { Comptoir, VenteDto } from './comptoir.service';
import { messageDErreur } from '../noyau/erreurs';
import type { ArticleDto, ClientDto } from '../noyau/api';

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
  ],
  templateUrl: './vente-au-comptoir.html',
})
export class VenteAuComptoir {
  private readonly service = inject(Comptoir);
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

  protected readonly total = computed(() =>
    this.panier().reduce((somme, l) => somme + (l.article.prixUnitaireHt ?? 0) * l.quantite, 0),
  );
  protected readonly articles = computed(() => this.panier().length);

  constructor() {
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
   * Enregistre la vente, puis emet sa facture.
   *
   * Deux appels et non un : la facture fige ce qu'elle doit, et l'emettre est un geste distinct
   * de la vente. Si elle echoue, la vente reste — la marchandise est partie, et l'effacer pour
   * un probleme de facturation serait perdre l'information la plus importante des deux.
   */
  protected vendre(): void {
    if (this.envoiEnCours() || this.panier().length === 0) {
      return;
    }
    this.envoiEnCours.set(true);
    this.erreur.set(null);

    const vente: VenteDto = {
      code: `V-${Date.now()}`,
      // Rejouable sans risque : reposter la meme reference rend la vente deja enregistree au lieu
      // d'en creer une seconde. Le comptoir aussi peut perdre sa reponse.
      referenceClient: crypto.randomUUID(),
      client: this.client() ?? undefined,
      ligneVente: this.panier().map((l) => ({
        article: { id: l.article.id },
        quantite: l.quantite,
        prixUnitaire: l.article.prixUnitaireHt,
      })),
    };

    this.service.vendre(vente).subscribe({
      next: (enregistree) => {
        this.service.facturer(enregistree.id!).subscribe({
          next: (facture) => {
            this.envoiEnCours.set(false);
            this.snack.open(
              `Vente enregistrée — facture ${facture.numero}, ${facture.totalTtc} F TTC`,
              'Fermer',
              { duration: 5000 },
            );
            this.viderLePanier();
          },
          error: () => {
            // La vente est passee : c'est ce qui compte, la marchandise est partie.
            this.envoiEnCours.set(false);
            this.snack.open(
              'Vente enregistrée, mais la facture n’a pas pu être émise.',
              'Fermer',
              { duration: 6000 },
            );
            this.viderLePanier();
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
}
