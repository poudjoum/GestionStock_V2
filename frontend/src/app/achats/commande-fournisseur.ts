import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Achats, ArticleDto, Tiers } from './achats.service';
import { Catalogue } from '../catalogue/catalogue.service';
import { Repertoire } from '../repertoire/repertoire.service';
import { messageDErreur } from '../noyau/erreurs';

/** Une ligne en cours de composition. `id` n'existe que pour celles deja enregistrees. */
interface LigneSaisie {
  id?: number;
  article: ArticleDto;
  quantite: number;
  prixAchat: number;
}

/**
 * Composer une commande fournisseur.
 *
 * C'etait le dernier maillon manquant : l'ecran des receptions savait recevoir une commande, pas
 * en passer une. Sans lui, la seule facon d'approvisionner le magasin etait d'appeler l'API a la
 * main.
 *
 * **Le prix saisi est le prix d'achat, et c'est le point a ne pas manquer.** Il alimente le cout
 * moyen de l'article, donc la valeur du stock au bilan. Le pre-remplir avec le prix de vente de
 * l'article — ce qui serait commode — gonflerait silencieusement la valorisation du magasin, et
 * personne ne s'en apercevrait avant l'inventaire. Le champ reste donc vide, et son libelle dit
 * ce qu'il attend.
 *
 * Deux moments, un seul ecran :
 *
 * - **Une commande neuve** se compose en local et part d'un seul envoi, lignes comprises. Tant
 *   qu'elle n'est pas enregistree, la fermer ne laisse rien derriere — ni commande vide, ni code
 *   reserve pour rien.
 * - **Un brouillon deja enregistre** se corrige ligne par ligne, comme l'API le permet. Chaque
 *   correction part quand on quitte le champ, jamais a chaque frappe : le prix « 4 » n'a pas a
 *   etre enregistre en chemin vers « 4500 ».
 *
 * Valider, c'est passer la commande au fournisseur : les lignes ne bougent plus ensuite.
 */
@Component({
  selector: 'app-commande-fournisseur',
  imports: [
    DecimalPipe,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    RouterLink,
  ],
  templateUrl: './commande-fournisseur.html',
})
export class CommandeFournisseur implements OnInit {
  private readonly service = inject(Achats);
  private readonly catalogue = inject(Catalogue);
  private readonly repertoire = inject(Repertoire);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly snack = inject(MatSnackBar);
  private readonly frappeArticle = new Subject<string>();
  private readonly frappeFournisseur = new Subject<string>();

  /** Nul tant que la commande n'est pas enregistree : on compose alors entierement en local. */
  protected readonly idCommande = signal<number | null>(null);
  protected readonly code = signal('');
  protected readonly fournisseur = signal<Tiers | null>(null);
  protected readonly lignes = signal<LigneSaisie[]>([]);
  protected readonly chargement = signal(false);
  protected readonly envoiEnCours = signal(false);
  protected readonly erreur = signal<string | null>(null);

  protected readonly rechercheFournisseur = signal('');
  protected readonly fournisseursTrouves = signal<Tiers[]>([]);
  protected readonly chercheFournisseur = signal(false);
  protected readonly rechercheArticle = signal('');
  protected readonly articlesTrouves = signal<ArticleDto[]>([]);
  protected readonly chercheArticle = signal(false);

  protected readonly enregistree = computed(() => this.idCommande() !== null);
  protected readonly articles = computed(() => this.lignes().length);
  protected readonly total = computed(() =>
    this.lignes().reduce((somme, l) => somme + l.quantite * l.prixAchat, 0),
  );
  /** Une ligne sans prix d'achat fausserait le cout moyen : on ne laisse pas valider. */
  protected readonly sansPrix = computed(() => this.lignes().filter((l) => l.prixAchat <= 0).length);
  protected readonly complet = computed(
    () => !!this.code().trim() && this.fournisseur() !== null && this.lignes().length > 0,
  );

  constructor() {
    this.frappeArticle
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((q) => this.catalogue.articles(q, null)),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (page) => {
          this.articlesTrouves.set(page.content ?? []);
          this.chercheArticle.set(false);
        },
        error: () => {
          this.articlesTrouves.set([]);
          this.chercheArticle.set(false);
        },
      });

    this.frappeFournisseur
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((q) => this.repertoire.lister('fournisseur', q)),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (page) => {
          this.fournisseursTrouves.set(page.content ?? []);
          this.chercheFournisseur.set(false);
        },
        error: () => {
          this.fournisseursTrouves.set([]);
          this.chercheFournisseur.set(false);
        },
      });
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.ouvrirLeBrouillon(Number(id));
    } else {
      // Un code lisible, date du jour. Le faire saisir reviendrait a demander d'inventer un
      // numero unique avant d'avoir commence ; il reste modifiable.
      this.code.set(codeParDefaut());
    }
  }

  // --- Le fournisseur -----------------------------------------------------------------------

  protected chercherFournisseur(q: string): void {
    this.rechercheFournisseur.set(q);
    if (!q.trim()) {
      this.fournisseursTrouves.set([]);
      this.chercheFournisseur.set(false);
      return;
    }
    this.chercheFournisseur.set(true);
    this.frappeFournisseur.next(q);
  }

  protected choisirFournisseur(f: Tiers | null): void {
    this.fournisseur.set(f);
    this.rechercheFournisseur.set('');
    this.fournisseursTrouves.set([]);
  }

  protected nomDuFournisseur(f: Tiers): string {
    return [f.nom, f.prenom].filter(Boolean).join(' ');
  }

  // --- Les lignes ---------------------------------------------------------------------------

  protected chercherArticle(q: string): void {
    this.rechercheArticle.set(q);
    if (!q.trim()) {
      this.articlesTrouves.set([]);
      this.chercheArticle.set(false);
      return;
    }
    this.chercheArticle.set(true);
    this.frappeArticle.next(q);
  }

  protected ajouter(article: ArticleDto): void {
    this.rechercheArticle.set('');
    this.articlesTrouves.set([]);

    if (this.lignes().some((l) => l.article.id === article.id)) {
      this.snack.open('Cet article est déjà dans la commande.', 'Fermer', { duration: 3000 });
      return;
    }
    // Le prix d'achat reste a zero : c'est au fournisseur de le dire, pas au catalogue.
    const nouvelle: LigneSaisie = { article, quantite: 1, prixAchat: 0 };

    const id = this.idCommande();
    if (id === null) {
      this.lignes.update((l) => [...l, nouvelle]);
      return;
    }
    this.service
      .ajouterLigne(id, { article: { id: article.id }, quantite: 1, prixUnitaire: 0 })
      .subscribe({
        next: (ligne) => this.lignes.update((l) => [...l, { ...nouvelle, id: ligne.id }]),
        error: (echec: unknown) =>
          this.erreur.set(messageDErreur(echec, "La ligne n'a pas pu être ajoutée.")),
      });
  }

  protected changerQuantite(index: number, quantite: number): void {
    this.majLigne(index, { quantite: Number.isFinite(quantite) ? quantite : 0 });
  }

  protected changerPrix(index: number, prix: number): void {
    this.majLigne(index, { prixAchat: Number.isFinite(prix) ? prix : 0 });
  }

  /**
   * Enregistre la ligne quand on quitte le champ.
   *
   * A chaque frappe, saisir « 4500 » enverrait 4, puis 45, puis 450 — et la commande aurait
   * porte, un instant, un prix d'achat faux.
   */
  protected ligneQuittee(index: number): void {
    // Un champ vide n'est pas une demande de retrait : on retire une ligne avec sa corbeille, pas
    // en effacant sa quantite. La valeur revient a un plutot que de faire disparaitre l'article.
    if ((this.lignes()[index]?.quantite ?? 0) <= 0) {
      this.majLigne(index, { quantite: 1 });
    }
    const id = this.idCommande();
    const ligne = this.lignes()[index];
    if (id === null || !ligne?.id) {
      return;
    }
    this.service
      .modifierLigne(id, ligne.id, { quantite: ligne.quantite, prixUnitaire: ligne.prixAchat })
      .subscribe({
        error: (echec: unknown) =>
          this.erreur.set(messageDErreur(echec, "La ligne n'a pas pu être corrigée.")),
      });
  }

  protected retirer(index: number): void {
    const ligne = this.lignes()[index];
    const id = this.idCommande();
    this.lignes.update((l) => l.filter((_, i) => i !== index));
    if (id !== null && ligne?.id) {
      this.service.retirerLigne(id, ligne.id).subscribe({
        error: (echec: unknown) =>
          this.erreur.set(messageDErreur(echec, "La ligne n'a pas pu être retirée.")),
      });
    }
  }

  // --- Enregistrer et valider ---------------------------------------------------------------

  protected enregistrer(): void {
    if (this.envoiEnCours() || !this.complet() || this.enregistree()) {
      return;
    }
    this.envoiEnCours.set(true);
    this.erreur.set(null);

    this.service
      .creer({
        code: this.code().trim(),
        // La date est exigee par l'API, et c'est bien celle du jour : une commande se passe au
        // moment ou on la saisit.
        dateCommande: new Date().toISOString(),
        fournisseur: { id: this.fournisseur()!.id },
        ligneCmndeFournisseur: this.lignes().map((l) => ({
          article: { id: l.article.id },
          quantite: l.quantite,
          prixUnitaire: l.prixAchat,
        })),
      })
      .subscribe({
        next: (creee) => {
          this.envoiEnCours.set(false);
          this.snack.open('Commande enregistrée en préparation.', 'Fermer', { duration: 3000 });
          // On reste sur l'ecran : la commande est enregistree, pas encore passee, et c'est
          // maintenant qu'on la relit avant de la valider. L'URL suit, pour que rafraichir
          // retrouve le brouillon plutot que d'en recommencer un vide.
          void this.router.navigate(['/achats', creee.id], { replaceUrl: true });
          this.ouvrirLeBrouillon(creee.id!);
        },
        error: (echec: unknown) => {
          this.envoiEnCours.set(false);
          this.erreur.set(messageDErreur(echec, "La commande n'a pas pu être enregistrée."));
        },
      });
  }

  protected valider(): void {
    const id = this.idCommande();
    if (id === null || this.envoiEnCours()) {
      return;
    }
    this.envoiEnCours.set(true);
    this.erreur.set(null);
    this.service.valider(id).subscribe({
      next: () => {
        this.envoiEnCours.set(false);
        this.snack.open('Commande passée : elle attend sa livraison.', 'Fermer', { duration: 4000 });
        void this.router.navigate(['/achats'], { queryParams: { vue: 'attendues' } });
      },
      error: (echec: unknown) => {
        this.envoiEnCours.set(false);
        this.erreur.set(messageDErreur(echec, "La commande n'a pas pu être validée."));
      },
    });
  }

  protected annuler(): void {
    const id = this.idCommande();
    if (id === null || this.envoiEnCours()) {
      return;
    }
    this.envoiEnCours.set(true);
    this.service.annuler(id).subscribe({
      next: () => {
        this.envoiEnCours.set(false);
        this.snack.open('Commande annulée.', 'Fermer', { duration: 3000 });
        void this.router.navigate(['/achats']);
      },
      error: (echec: unknown) => {
        this.envoiEnCours.set(false);
        this.erreur.set(messageDErreur(echec, "La commande n'a pas pu être annulée."));
      },
    });
  }

  protected retour(): void {
    void this.router.navigate(['/achats']);
  }

  private majLigne(index: number, changement: Partial<LigneSaisie>): void {
    this.lignes.update((l) => l.map((ligne, i) => (i === index ? { ...ligne, ...changement } : ligne)));
  }

  private ouvrirLeBrouillon(id: number): void {
    this.idCommande.set(id);
    this.chargement.set(true);
    this.erreur.set(null);

    this.service.detail(id).subscribe({
      next: (commande) => {
        // Une commande deja passee ne se compose plus : on l'ouvre la ou elle se travaille
        // desormais, au quai, plutot que de montrer un formulaire que l'API refusera.
        if (commande.etat && commande.etat !== 'EN_PREPARATION') {
          void this.router.navigate(['/achats', id, 'reception'], { replaceUrl: true });
          return;
        }
        this.code.set(commande.code ?? '');
        const f = commande.fournisseur;
        this.fournisseur.set(
          f ? { id: f.id, nom: f.nom ?? '', prenom: f.prenom ?? '', mail: '', tel: '' } : null,
        );
        this.chargement.set(false);
      },
      error: (echec: unknown) => {
        this.chargement.set(false);
        this.erreur.set(messageDErreur(echec, 'La commande n’a pas pu être chargée.'));
      },
    });

    this.service.lignes(id).subscribe({
      next: (lignes) =>
        this.lignes.set(
          lignes.map((l) => ({
            id: l.id,
            article: l.article ?? {},
            quantite: Number(l.quantite ?? 0),
            prixAchat: Number(l.prixUnitaire ?? 0),
          })),
        ),
      error: () => this.lignes.set([]),
    });
  }
}

/** « BC-20260922-0714 » : lisible, trie par date, et distinct d'une commande a l'autre. */
function codeParDefaut(): string {
  const d = new Date();
  const p = (n: number) => `${n}`.padStart(2, '0');
  return `BC-${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}`;
}
