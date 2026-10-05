import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EnTetePage, EtatVide, OptionSelecteur, Selecteur, Statut, TonStatut } from '../design';
import { Catalogue } from '../catalogue/catalogue.service';
import { messageDErreur } from '../noyau/erreurs';
import type { ArticleDto } from '../noyau/api';
import { ConditionnementDto, libelleDeLigne, unites } from '../noyau/conditionnements';
import { SiteDto, Sites } from '../noyau/sites';
import { EtatTransfert, LigneTransfertDto, ServiceTransferts, TransfertDto } from './transferts.service';

type Vue = 'preparer' | 'route' | 'recus';

/** Ce qu'on saisit a la reception d'une ligne : ce qui est descendu du camion, et pourquoi il manque. */
interface Reception {
  idLigne: number;
  quantite: number | null;
  motif: string;
}

const ETATS: Record<EtatTransfert, { libelle: string; ton: TonStatut; icone: string }> = {
  BROUILLON: { libelle: 'En préparation', ton: 'neutre', icone: 'edit_note' },
  EXPEDIE: { libelle: 'En route', ton: 'alerte', icone: 'local_shipping' },
  RECU: { libelle: 'Reçu', ton: 'ok', icone: 'task_alt' },
  ANNULE: { libelle: 'Annulé', ton: 'neutre', icone: 'block' },
};

/**
 * Les transferts entre sites : l'entrepot reapprovisionne le magasin, un magasin en depanne un
 * autre.
 *
 * Deux equipes, deux gestes. Au depart, le magasinier compose le chargement et l'expedie : le
 * stock du depot baisse. A l'arrivee, son collegue compte ce qui descend du camion : c'est cela
 * qui entre, et ce qui manque se dit — une casse en route ne disparait pas des comptes en silence.
 */
@Component({
  selector: 'app-transferts',
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    EnTetePage,
    EtatVide,
    Selecteur,
    Statut,
  ],
  templateUrl: './transferts.html',
})
export class Transferts implements OnInit {
  private readonly service = inject(ServiceTransferts);
  private readonly catalogue = inject(Catalogue);
  private readonly sitesService = inject(Sites);
  private readonly snack = inject(MatSnackBar);
  private readonly frappe = new Subject<string>();

  protected readonly etats = ETATS;
  protected readonly libelleDeLigne = libelleDeLigne;
  protected readonly unites = unites;

  protected readonly liste = signal<TransfertDto[]>([]);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);
  protected readonly vue = signal<Vue>('preparer');

  /** Le transfert ouvert dans le volet ; `nouveau` pour un transfert pas encore cree. */
  protected readonly ouvert = signal<TransfertDto | null>(null);
  protected readonly nouveau = signal(false);
  protected readonly destination = signal<number | null>(null);
  protected readonly commentaire = signal('');
  protected readonly envoi = signal(false);
  protected readonly erreurVolet = signal<string | null>(null);

  protected readonly recherche = signal('');
  protected readonly trouves = signal<ArticleDto[]>([]);
  protected readonly receptions = signal<Reception[]>([]);

  protected readonly actif = this.sitesService.actif;
  /** Les autres sites de l'entreprise : on transfere de celui-ci vers l'un d'eux. */
  protected readonly destinations = signal<SiteDto[]>([]);

  protected readonly parVue = computed(() => {
    const ici = this.actif()?.id;
    const tous = this.liste();
    return {
      preparer: tous.filter((t) => t.etat === 'BROUILLON'),
      route: tous.filter((t) => t.etat === 'EXPEDIE'),
      recus: tous.filter((t) => t.etat === 'RECU' || t.etat === 'ANNULE'),
      // A recevoir ici : ce qui roule vers le site actif.
      aRecevoirIci: tous.filter((t) => t.etat === 'EXPEDIE' && t.idSiteDestination === ici).length,
    };
  });

  protected readonly vues = computed<OptionSelecteur<Vue>[]>(() => [
    { valeur: 'preparer', libelle: 'En préparation', compteur: this.parVue().preparer.length },
    { valeur: 'route', libelle: 'En route', compteur: this.parVue().route.length },
    { valeur: 'recus', libelle: 'Terminés', compteur: this.parVue().recus.length },
  ]);

  protected readonly affiches = computed(() => this.parVue()[this.vue()]);

  /** Le transfert ouvert part-il d'ici — c'est-a-dire, puis-je le preparer et l'expedier ? */
  protected readonly auDepart = computed(() => this.ouvert()?.idSiteSource === this.actif()?.id);
  protected readonly aLArrivee = computed(() => this.ouvert()?.idSiteDestination === this.actif()?.id);

  constructor() {
    this.frappe
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((q) => this.catalogue.articles(q, null, 0, 8)),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (page) => this.trouves.set(this.recherche().trim() ? (page.content ?? []) : []),
        error: () => this.trouves.set([]),
      });
  }

  ngOnInit(): void {
    this.charger();
    this.sitesService.tous().subscribe({
      next: (sites) => this.destinations.set(sites.filter((s) => s.actif !== false)),
      // Le magasinier ne lit pas la liste complete des sites : il a ceux qu'il voit.
      error: () => this.destinations.set(this.sitesService.sites()),
    });
  }

  protected autresSites(): SiteDto[] {
    return this.destinations().filter((s) => s.id !== this.actif()?.id);
  }

  protected etat(t: TransfertDto) {
    return ETATS[t.etat ?? 'BROUILLON'];
  }

  protected nombreDeLignes(t: TransfertDto): number {
    return t.lignes?.length ?? 0;
  }

  // --- Le volet -----------------------------------------------------------------------------

  protected creer(): void {
    this.ouvert.set(null);
    this.nouveau.set(true);
    this.destination.set(this.autresSites()[0]?.id ?? null);
    this.commentaire.set('');
    this.erreurVolet.set(null);
  }

  protected ouvrir(t: TransfertDto): void {
    this.nouveau.set(false);
    this.ouvert.set(t);
    this.erreurVolet.set(null);
    this.recherche.set('');
    this.trouves.set([]);
    this.receptions.set(
      (t.lignes ?? []).map((l) => ({ idLigne: l.id!, quantite: l.quantite ?? null, motif: '' })),
    );
  }

  protected fermer(): void {
    this.ouvert.set(null);
    this.nouveau.set(false);
  }

  /** Le brouillon nait vide, avec sa destination : les lignes s'ajoutent ensuite, une a une. */
  protected demarrer(): void {
    const destination = this.destination();
    if (!destination || this.envoi()) {
      return;
    }
    this.executer(
      this.service.creer({ idSiteDestination: destination, commentaire: this.commentaire().trim() || undefined }),
      (t) => {
        this.nouveau.set(false);
        this.ouvrir(t);
      },
    );
  }

  protected chercher(q: string): void {
    this.recherche.set(q);
    if (!q.trim()) {
      this.trouves.set([]);
      return;
    }
    this.frappe.next(q);
  }

  /** On charge en general au carton : le plus grand conditionnement est propose d'abord. */
  protected ajouter(article: ArticleDto): void {
    const t = this.ouvert();
    if (!t?.id) {
      return;
    }
    const carton = [...(article.conditionnements ?? [])]
      .filter((c) => c.actif !== false)
      .sort((a, b) => (b.quantiteUnites ?? 0) - (a.quantiteUnites ?? 0))[0];
    this.recherche.set('');
    this.trouves.set([]);
    this.executer(
      this.service.ajouterLigne(t.id, {
        article: { id: article.id },
        conditionnement: carton ? { id: carton.id } : undefined,
        quantite: 1,
      }),
      (maj) => this.ouvrir(maj),
    );
  }

  /** Changer la quantite ou le conditionnement d'une ligne : on la remplace. */
  protected corriger(ligne: LigneTransfertDto, quantite: number | null, conditionnement: ConditionnementDto | null | undefined): void {
    const t = this.ouvert();
    if (!t?.id || !ligne.id || !quantite || quantite <= 0) {
      return;
    }
    this.executer(this.service.retirerLigne(t.id, ligne.id), () =>
      this.executer(
        this.service.ajouterLigne(t.id!, {
          article: { id: ligne.article?.id },
          conditionnement: conditionnement?.id ? { id: conditionnement.id } : undefined,
          quantite,
        }),
        (maj) => this.ouvrir(maj),
      ),
    );
  }

  protected conditionnementsDe(ligne: LigneTransfertDto): ConditionnementDto[] {
    return (ligne.article?.conditionnements ?? []).filter((c) => c.actif !== false);
  }

  protected changerUnite(ligne: LigneTransfertDto, idConditionnement: number): void {
    const conditionnement = idConditionnement ? { id: idConditionnement } : null;
    this.corriger(ligne, Math.max(1, Math.round(ligne.quantite ?? 1)), conditionnement);
  }

  protected retirer(ligne: LigneTransfertDto): void {
    const t = this.ouvert();
    if (t?.id && ligne.id) {
      this.executer(this.service.retirerLigne(t.id, ligne.id), (maj) => this.ouvrir(maj));
    }
  }

  protected expedier(): void {
    const t = this.ouvert();
    if (t?.id) {
      this.executer(this.service.expedier(t.id), (maj) => {
        this.snack.open(`${maj.reference} est parti pour ${maj.nomSiteDestination}.`, 'Fermer', { duration: 4000 });
        this.vue.set('route');
        this.ouvrir(maj);
      });
    }
  }

  protected annuler(): void {
    const t = this.ouvert();
    if (t?.id) {
      this.executer(this.service.annuler(t.id), () => this.fermer());
    }
  }

  protected saisirReception(idLigne: number, changement: Partial<Reception>): void {
    this.receptions.update((liste) => liste.map((r) => (r.idLigne === idLigne ? { ...r, ...changement } : r)));
  }

  protected receptionDe(idLigne: number | undefined): Reception | undefined {
    return this.receptions().find((r) => r.idLigne === idLigne);
  }

  protected manque(ligne: LigneTransfertDto): boolean {
    const r = this.receptionDe(ligne.id);
    return r != null && r.quantite != null && r.quantite < (ligne.quantite ?? 0);
  }

  /** Tout ce qui manque est motive, et rien n'est compte plus qu'il n'est parti. */
  protected readonly receptionComplete = computed(() => {
    const t = this.ouvert();
    return (t?.lignes ?? []).every((l) => {
      const r = this.receptionDe(l.id);
      if (!r || r.quantite == null || r.quantite < 0 || r.quantite > (l.quantite ?? 0)) {
        return false;
      }
      return r.quantite === l.quantite || !!r.motif.trim();
    });
  });

  protected recevoir(): void {
    const t = this.ouvert();
    if (!t?.id || !this.receptionComplete()) {
      return;
    }
    this.executer(
      this.service.recevoir(
        t.id,
        this.receptions().map((r) => ({
          idLigne: r.idLigne,
          quantiteRecue: r.quantite ?? 0,
          motifEcart: r.motif.trim() || undefined,
        })),
      ),
      (maj) => {
        this.snack.open(`${maj.reference} reçu : le stock de ${maj.nomSiteDestination} est à jour.`, 'Fermer', {
          duration: 4000,
        });
        this.vue.set('recus');
        this.ouvrir(maj);
      },
    );
  }

  /** Un appel au serveur, son message d'erreur s'il refuse, puis la liste relue. */
  private executer(appel: ReturnType<ServiceTransferts['expedier']>, suite: (t: TransfertDto) => void): void {
    this.envoi.set(true);
    this.erreurVolet.set(null);
    appel.subscribe({
      next: (t) => {
        this.envoi.set(false);
        suite(t);
        this.charger();
      },
      error: (echec: unknown) => {
        this.envoi.set(false);
        // « Stock insuffisant… à « Dépôt Bonabéri » » : le serveur dit quelle ligne manque.
        this.erreurVolet.set(messageDErreur(echec, 'Le transfert n’a pas pu être enregistré.'));
      },
    });
  }

  private charger(): void {
    this.chargement.set(this.liste().length === 0);
    this.erreur.set(null);
    this.service.lister().subscribe({
      next: (liste) => {
        this.liste.set(liste);
        this.chargement.set(false);
        // A l'ouverture, ce qui attend d'etre recu ici passe devant.
        if (this.parVue().aRecevoirIci > 0 && this.vue() === 'preparer' && !this.parVue().preparer.length) {
          this.vue.set('route');
        }
      },
      error: (echec: unknown) => {
        this.chargement.set(false);
        this.erreur.set(messageDErreur(echec, 'Les transferts n’ont pas pu être chargés.'));
      },
    });
  }
}
