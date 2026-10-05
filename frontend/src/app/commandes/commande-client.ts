import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommandeClientDto, CommandesClients, EtatCommande, LigneCommandeClientDto } from './commandes.service';
import { statutDeCommandeClient } from './statuts';
import { Catalogue } from '../catalogue/catalogue.service';
import { Repertoire, Tiers } from '../repertoire/repertoire.service';
import { Session } from '../noyau/session';
import { Sites } from '../noyau/sites';
import { messageDErreur } from '../noyau/erreurs';
import { ConditionnementDto, libelleDeLigne, vendables } from '../noyau/conditionnements';
import type { components } from '../api/schema';
import { Statut } from '../design';

type ArticleDto = components['schemas']['ArticleDto'];

/** Une ligne en composition. `id` n'existe que pour celles deja enregistrees. */
interface LigneSaisie {
  id?: number;
  article: ArticleDto;
  conditionnement: ConditionnementDto | null;
  quantite: number;
  prix: number;
}

/** Une ligne d'une commande validee, avec ce qu'on s'apprete a servir. */
interface LigneAServir {
  ligne: LigneCommandeClientDto;
  aServir: number | null;
}

/**
 * Une commande client, de sa prise a son service.
 *
 * Trois moments sur un ecran : on la **compose** (client, articles au prix du catalogue, site qui
 * livre), on la **valide** — la marchandise est alors reservee dans le site qui livre, et le
 * comptoir ne peut plus la vendre —, puis on la **sert**, en une fois ou en plusieurs. Une
 * commande terminee se relit.
 *
 * Le prix propose est celui du catalogue, du carton si l'on vend au carton : c'est un prix de vente.
 * Il reste modifiable tant que la commande n'est pas enregistree — une remise de gros se negocie au
 * moment ou l'on prend la commande.
 */
@Component({
  selector: 'app-commande-client',
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    Statut,
  ],
  templateUrl: './commande-client.html',
})
export class CommandeClient implements OnInit {
  private readonly service = inject(CommandesClients);
  private readonly catalogue = inject(Catalogue);
  private readonly repertoire = inject(Repertoire);
  private readonly session = inject(Session);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly snack = inject(MatSnackBar);
  private readonly sitesService = inject(Sites);
  private readonly frappeArticle = new Subject<string>();
  private readonly frappeClient = new Subject<string>();

  protected readonly commande = signal<CommandeClientDto | null>(null);
  protected readonly etat = computed<EtatCommande>(() => this.commande()?.etat ?? 'EN_PREPARATION');
  protected readonly brouillon = computed(() => this.etat() === 'EN_PREPARATION');
  protected readonly aServir = computed(() => this.etat() === 'VALIDEE' || this.etat() === 'PARTIELLEMENT_LIVREE');
  protected readonly enregistree = computed(() => !!this.commande()?.id);
  protected readonly statut = computed(() => statutDeCommandeClient({ etat: this.etat() }));

  protected readonly code = signal('');
  protected readonly client = signal<Tiers | null>(null);
  protected readonly lignes = signal<LigneSaisie[]>([]);
  protected readonly aServirLignes = signal<LigneAServir[]>([]);
  protected readonly chargement = signal(false);
  protected readonly envoi = signal(false);
  protected readonly erreur = signal<string | null>(null);

  protected readonly rechercheClient = signal('');
  protected readonly clientsTrouves = signal<Tiers[]>([]);
  protected readonly rechercheArticle = signal('');
  protected readonly articlesTrouves = signal<ArticleDto[]>([]);

  /** Les sites qui peuvent livrer : tous ceux de l'appelant, entrepots compris. */
  protected readonly sites = this.sitesService.sites;
  protected readonly plusieursSites = this.sitesService.plusieurs;
  private readonly choixExpedition = signal<number | null>(null);
  protected readonly siteExpedition = computed(
    () => this.choixExpedition() ?? this.commande()?.idSiteExpedition ?? this.sitesService.actif()?.id ?? null,
  );

  protected readonly cloture = signal(false);
  protected readonly motif = signal('');
  /** Renoncer a un reliquat est une decision de gerant ; le serveur le redit. */
  protected readonly peutCloturer = computed(() =>
    this.session.roles().some((r) => r === 'ROLE_ADMIN' || r === 'ROLE_MANAGER'),
  );

  protected readonly libelleDeLigne = libelleDeLigne;
  protected readonly vendables = vendables;
  protected readonly total = computed(() => this.lignes().reduce((s, l) => s + l.quantite * l.prix, 0));
  protected readonly complet = computed(
    () => !!this.code().trim() && this.client() !== null && this.lignes().length > 0,
  );
  protected readonly quelqueChoseAServir = computed(() => this.aServirLignes().some((s) => (s.aServir ?? 0) > 0));

  constructor() {
    this.frappeArticle
      .pipe(debounceTime(300), distinctUntilChanged(), switchMap((q) => this.catalogue.articles(q, null)), takeUntilDestroyed())
      .subscribe({
        next: (page) => this.articlesTrouves.set(page.content ?? []),
        error: () => this.articlesTrouves.set([]),
      });
    this.frappeClient
      .pipe(debounceTime(300), distinctUntilChanged(), switchMap((q) => this.repertoire.lister('client', q)), takeUntilDestroyed())
      .subscribe({
        next: (page) => this.clientsTrouves.set(page.content ?? []),
        error: () => this.clientsTrouves.set([]),
      });
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.charger(Number(id));
    } else {
      this.code.set(codeParDefaut());
    }
  }

  protected retour(): void {
    void this.router.navigate(['/commandes'], {
      queryParams: { vue: this.aServir() ? 'a-servir' : this.brouillon() ? 'preparation' : 'terminees' },
    });
  }

  // --- Composer -------------------------------------------------------------------------------

  protected chercherClient(q: string): void {
    this.rechercheClient.set(q);
    if (q.trim()) {
      this.frappeClient.next(q);
    } else {
      this.clientsTrouves.set([]);
    }
  }

  protected choisirClient(c: Tiers | null): void {
    this.client.set(c);
    this.rechercheClient.set('');
    this.clientsTrouves.set([]);
  }

  protected nomDuClient(c: Tiers | null): string {
    return c ? [c.prenom, c.nom].filter(Boolean).join(' ') : '';
  }

  protected chercherArticle(q: string): void {
    this.rechercheArticle.set(q);
    if (q.trim()) {
      this.frappeArticle.next(q);
    } else {
      this.articlesTrouves.set([]);
    }
  }

  protected ajouter(article: ArticleDto): void {
    this.rechercheArticle.set('');
    this.articlesTrouves.set([]);
    const nouvelle: LigneSaisie = { article, conditionnement: null, quantite: 1, prix: prixCatalogue(article, null) };
    const id = this.commande()?.id;
    if (!id) {
      this.lignes.update((l) => [...l, nouvelle]);
      return;
    }
    this.service
      .ajouterLigne(id, { article: { id: article.id }, quantite: 1, prixUnitaire: nouvelle.prix })
      .subscribe({
        next: (ligne) => this.lignes.update((l) => [...l, { ...nouvelle, id: ligne.id }]),
        error: (echec: unknown) => this.erreur.set(messageDErreur(echec, 'La ligne n’a pas pu être ajoutée.')),
      });
  }

  /** Vendre au carton plutot qu'a l'unite : le prix suit, c'est celui du carton. Avant enregistrement seulement. */
  protected choisirConditionnement(index: number, idConditionnement: number | null): void {
    const ligne = this.lignes()[index];
    const conditionnement = idConditionnement
      ? (vendables(ligne.article).find((c) => c.id === idConditionnement) ?? null)
      : null;
    this.majLigne(index, { conditionnement, prix: prixCatalogue(ligne.article, conditionnement) });
  }

  protected changerQuantite(index: number, valeur: number): void {
    this.majLigne(index, { quantite: Number.isFinite(valeur) ? valeur : 0 });
  }

  protected changerPrix(index: number, valeur: number): void {
    this.majLigne(index, { prix: Number.isFinite(valeur) ? valeur : 0 });
  }

  /** La quantite d'une ligne enregistree part quand on quitte le champ, pas a chaque frappe. */
  protected quantiteQuittee(index: number): void {
    const ligne = this.lignes()[index];
    if ((ligne?.quantite ?? 0) <= 0) {
      this.majLigne(index, { quantite: 1 });
    }
    const id = this.commande()?.id;
    if (!id || !ligne?.id) {
      return;
    }
    this.service.modifierQuantite(id, ligne.id, this.lignes()[index].quantite).subscribe({
      error: (echec: unknown) => this.erreur.set(messageDErreur(echec, 'La ligne n’a pas pu être corrigée.')),
    });
  }

  protected retirer(index: number): void {
    const ligne = this.lignes()[index];
    const id = this.commande()?.id;
    this.lignes.update((l) => l.filter((_, i) => i !== index));
    if (id && ligne?.id) {
      this.service.retirerLigne(id, ligne.id).subscribe({
        error: (echec: unknown) => this.erreur.set(messageDErreur(echec, 'La ligne n’a pas pu être retirée.')),
      });
    }
  }

  protected choisirExpedition(idSite: number): void {
    this.choixExpedition.set(idSite);
    const id = this.commande()?.id;
    if (!id) {
      return;
    }
    this.service.changerSiteExpedition(id, idSite).subscribe({
      next: (commande) => {
        this.commande.set(commande);
        this.snack.open(`Livrée par ${commande.nomSiteExpedition}.`, 'Fermer', { duration: 3000 });
      },
      error: (echec: unknown) => {
        this.choixExpedition.set(null);
        this.erreur.set(messageDErreur(echec, 'Le site qui livre n’a pas pu être changé.'));
      },
    });
  }

  protected enregistrer(): void {
    if (this.envoi() || !this.complet() || this.enregistree()) {
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    this.service
      .creer({
        code: this.code().trim(),
        dateCmnde: new Date().toISOString(),
        client: { id: this.client()!.id },
        idSiteExpedition: this.siteExpedition() ?? undefined,
        ligneCmndeClients: this.lignes().map((l) => ({
          article: { id: l.article.id },
          conditionnement: l.conditionnement ? { id: l.conditionnement.id } : undefined,
          quantite: l.quantite,
          prixUnitaire: l.prix,
        })),
      })
      .subscribe({
        next: (creee) => {
          this.envoi.set(false);
          this.snack.open('Commande enregistrée en préparation.', 'Fermer', { duration: 3000 });
          void this.router.navigate(['/commandes', creee.id], { replaceUrl: true });
          this.charger(creee.id!);
        },
        error: (echec: unknown) => {
          this.envoi.set(false);
          this.erreur.set(messageDErreur(echec, 'La commande n’a pas pu être enregistrée.'));
        },
      });
  }

  protected changerEtat(etat: 'VALIDEE' | 'ANNULEE'): void {
    const id = this.commande()?.id;
    if (!id || this.envoi()) {
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    this.service.changerEtat(id, etat).subscribe({
      next: () => {
        this.envoi.set(false);
        this.snack.open(
          etat === 'VALIDEE' ? 'Commande validée : la marchandise est réservée pour le client.' : 'Commande annulée.',
          'Fermer',
          { duration: 4000 },
        );
        this.charger(id);
      },
      error: (echec: unknown) => {
        this.envoi.set(false);
        this.erreur.set(messageDErreur(echec, 'La commande n’a pas pu changer d’état.'));
      },
    });
  }

  // --- Servir ---------------------------------------------------------------------------------

  protected saisirAServir(index: number, valeur: string): void {
    const n = valeur === '' ? null : Number(valeur);
    this.aServirLignes.update((l) => l.map((s, i) => (i === index ? { ...s, aServir: Number.isFinite(n!) ? n : null } : s)));
  }

  protected reste(ligne: LigneCommandeClientDto): number {
    return Number(ligne.resteAServir ?? 0);
  }

  protected toutServir(): void {
    this.aServirLignes.update((l) => l.map((s) => ({ ...s, aServir: this.reste(s.ligne) > 0 ? this.reste(s.ligne) : null })));
  }

  protected servir(): void {
    const id = this.commande()?.id;
    if (!id || this.envoi() || !this.quelqueChoseAServir()) {
      return;
    }
    if (this.aServirLignes().some((s) => (s.aServir ?? 0) > this.reste(s.ligne))) {
      this.erreur.set('Une quantité dépasse ce qui reste dû au client.');
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    const lignes = this.aServirLignes()
      .filter((s) => (s.aServir ?? 0) > 0)
      .map((s) => ({ idLigne: s.ligne.id!, quantite: s.aServir! }));
    this.service.servir(id, lignes).subscribe({
      next: (vente) => {
        this.envoi.set(false);
        this.snack.open(`Servi : la vente ${vente.code ?? ''} est enregistrée.`, 'Fermer', { duration: 5000 });
        this.charger(id);
      },
      error: (echec: unknown) => {
        this.envoi.set(false);
        // « Stock insuffisant à « Dépôt » : 3 disponibles » — le serveur dit quoi et ou.
        this.erreur.set(messageDErreur(echec, 'La commande n’a pas pu être servie.'));
      },
    });
  }

  protected cloturer(): void {
    const id = this.commande()?.id;
    if (!id || this.envoi() || !this.motif().trim()) {
      return;
    }
    this.envoi.set(true);
    this.service.cloturer(id, this.motif().trim()).subscribe({
      next: () => {
        this.envoi.set(false);
        this.snack.open('Reliquat clôturé : la réservation est levée.', 'Fermer', { duration: 4000 });
        this.charger(id);
      },
      error: (echec: unknown) => {
        this.envoi.set(false);
        this.erreur.set(messageDErreur(echec, 'Le reliquat n’a pas pu être clôturé.'));
      },
    });
  }

  private majLigne(index: number, changement: Partial<LigneSaisie>): void {
    this.lignes.update((l) => l.map((ligne, i) => (i === index ? { ...ligne, ...changement } : ligne)));
  }

  private charger(id: number): void {
    this.chargement.set(true);
    this.erreur.set(null);
    this.cloture.set(false);
    this.motif.set('');
    this.choixExpedition.set(null);
    this.service.detail(id).subscribe({
      next: (commande) => {
        this.commande.set(commande);
        this.code.set(commande.code ?? '');
        const c = commande.client;
        this.client.set(c ? { id: c.id, nom: c.nom ?? '', prenom: c.prenoms ?? '', mail: '', tel: c.numTel ?? '' } : null);
        this.service.lignes(id).subscribe({
          next: (lignes) => {
            this.lignes.set(
              lignes.map((l) => ({
                id: l.id,
                article: l.article ?? {},
                conditionnement: l.conditionnement ?? null,
                quantite: Number(l.quantite ?? 0),
                prix: Number(l.prixUnitaire ?? 0),
              })),
            );
            this.aServirLignes.set(lignes.map((ligne) => ({ ligne, aServir: null })));
            this.chargement.set(false);
          },
          error: () => {
            this.chargement.set(false);
            this.erreur.set('Les lignes de la commande n’ont pas pu être chargées.');
          },
        });
      },
      error: (echec: unknown) => {
        this.chargement.set(false);
        this.erreur.set(messageDErreur(echec, 'La commande n’a pas pu être chargée.'));
      },
    });
  }
}

/** Le prix de vente du catalogue : celui du conditionnement, a defaut celui de l'unite. */
function prixCatalogue(article: ArticleDto, conditionnement: ConditionnementDto | null): number {
  return Number(conditionnement?.prixVenteHt ?? article.prixUnitaireHt ?? 0);
}

/** « CC-20261005-1432 » : lisible, trie par date, modifiable. */
function codeParDefaut(): string {
  const d = new Date();
  const p = (n: number) => `${n}`.padStart(2, '0');
  return `CC-${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}`;
}
