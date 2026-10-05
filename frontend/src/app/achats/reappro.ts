import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { environnement } from '../../environnements/environnement';
import type { components } from '../api/schema';
import { Repertoire, Tiers } from '../repertoire/repertoire.service';
import { Sites } from '../noyau/sites';
import { messageDErreur } from '../noyau/erreurs';
import { symbole, unites } from '../noyau/conditionnements';
import type { StatutAffiche } from '../noyau/statuts';
import { EnTetePage, EtatVide, Section, Statut } from '../design';

type ReapproDto = components['schemas']['ReapproDto'];
type LigneReappro = components['schemas']['LigneReappro'];
type CommandeFourDto = components['schemas']['CommandeFourDto'];

const RACINE = `${environnement.api}/gestiondestock/v1/reappro`;

/** Une ligne de la proposition, telle que le gerant l'ajuste. */
interface Choix {
  ligne: LigneReappro;
  retenue: boolean;
  quantite: number | null;
  prix: number | null;
  idFournisseur: number | null;
}

const RAISONS: Record<string, StatutAffiche> = {
  RUPTURE: { libelle: 'Plus rien à vendre', ton: 'danger', icone: 'remove_shopping_cart' },
  SOUS_SEUIL: { libelle: 'Sous le seuil', ton: 'alerte', icone: 'trending_down' },
  A_COUVRIR: { libelle: 'Ne tiendra pas', ton: 'alerte', icone: 'schedule' },
};

/**
 * Le reapprovisionnement : ce qu'il faut commander pour tenir, calcule sur les ventes.
 *
 * Ecran de bureau, celui du gerant le lundi matin. Le calcul propose, le gerant decide : chaque
 * ligne se coche, sa quantite et son prix se corrigent, et le fournisseur se choisit quand
 * l'article n'a jamais ete achete. Un seul bouton cree les commandes — une par fournisseur, en
 * preparation : rien ne part chez personne sans etre relu et valide dans les achats.
 */
@Component({
  selector: 'app-reappro',
  imports: [
    DecimalPipe,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    EnTetePage,
    EtatVide,
    Section,
    Statut,
  ],
  templateUrl: './reappro.html',
  styles: `
    .chiffre {
      font: 600 var(--gs-texte-md) var(--gs-police-titre);
    }
    .detail {
      display: block;
      font-size: var(--gs-texte-xs);
      color: var(--gs-encre-3);
    }
    tr.ecartee td:not(:first-child) {
      opacity: 0.45;
    }
  `,
})
export class Reappro implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly repertoire = inject(Repertoire);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);
  private readonly sites = inject(Sites);

  protected readonly proposition = signal<ReapproDto | null>(null);
  protected readonly choix = signal<Choix[]>([]);
  protected readonly fournisseurs = signal<Tiers[]>([]);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);
  protected readonly envoi = signal(false);
  protected readonly erreurEnvoi = signal<string | null>(null);

  protected readonly symbole = symbole;
  protected readonly unites = unites;
  protected readonly siteActif = this.sites.actif;

  protected readonly retenues = computed(() => this.choix().filter((c) => c.retenue && (c.quantite ?? 0) > 0));
  protected readonly montant = computed(() =>
    this.retenues().reduce((s, c) => s + (c.quantite ?? 0) * (c.prix ?? 0), 0),
  );
  protected readonly nombreDeCommandes = computed(
    () => new Set(this.retenues().map((c) => c.idFournisseur).filter((f) => f != null)).size,
  );
  protected readonly sansFournisseur = computed(() => this.retenues().filter((c) => c.idFournisseur == null).length);
  protected readonly ruptures = computed(() => this.choix().filter((c) => c.ligne.raison === 'RUPTURE').length);

  ngOnInit(): void {
    this.charger();
    // Les fournisseurs, pour choisir celui d'un article jamais achete. Un commerce en a quelques
    // dizaines : une liste suffit, sans recherche.
    this.repertoire.lister('fournisseur', '', 0, 200).subscribe({
      next: (page) => this.fournisseurs.set(page.content ?? []),
      error: () => this.fournisseurs.set([]),
    });
  }

  protected raison(ligne: LigneReappro): StatutAffiche {
    return RAISONS[ligne.raison ?? 'A_COUVRIR'] ?? RAISONS['A_COUVRIR'];
  }

  protected unite(ligne: LigneReappro): string {
    return ligne.conditionnement?.libelle ?? symbole(ligne);
  }

  protected nomFournisseur(f: Tiers): string {
    return [f.nom, f.prenom].filter(Boolean).join(' ');
  }

  protected maj(index: number, changement: Partial<Choix>): void {
    this.choix.update((l) => l.map((c, i) => (i === index ? { ...c, ...changement } : c)));
  }

  protected nombre(valeur: unknown): number | null {
    const n = valeur === '' || valeur == null ? NaN : Number(valeur);
    return Number.isFinite(n) ? n : null;
  }

  protected toutCocher(retenue: boolean): void {
    this.choix.update((l) => l.map((c) => ({ ...c, retenue })));
  }

  protected creer(): void {
    if (this.envoi() || this.retenues().length === 0) {
      return;
    }
    if (this.sansFournisseur() > 0) {
      this.erreurEnvoi.set('Choisissez le fournisseur des articles qui n’en ont pas, ou décochez-les.');
      return;
    }
    this.envoi.set(true);
    this.erreurEnvoi.set(null);
    const lignes = this.retenues().map((c) => ({
      idArticle: c.ligne.idArticle,
      idFournisseur: c.idFournisseur,
      idConditionnement: c.ligne.conditionnement?.id ?? null,
      quantite: c.quantite,
      prixUnitaire: c.prix ?? 0,
    }));
    this.http.post<CommandeFourDto[]>(`${RACINE}/commandes`, { lignes }).subscribe({
      next: (creees) => {
        this.envoi.set(false);
        const annonce = this.snack.open(
          `${creees.length} commande${creees.length > 1 ? 's' : ''} en préparation : relisez-les avant de les passer.`,
          'Voir',
          { duration: 8000 },
        );
        annonce.onAction().subscribe(() =>
          void this.router.navigate(['/achats'], { queryParams: { vue: 'brouillons' } }),
        );
        this.charger();
      },
      error: (echec: unknown) => {
        this.envoi.set(false);
        this.erreurEnvoi.set(messageDErreur(echec, 'Les commandes n’ont pas pu être créées.'));
      },
    });
  }

  private charger(): void {
    this.chargement.set(true);
    this.erreur.set(null);
    this.http.get<ReapproDto>(RACINE).subscribe({
      next: (proposition) => {
        this.proposition.set(proposition);
        this.choix.set(
          (proposition.lignes ?? []).map((ligne) => ({
            ligne,
            // Un article sans fournisseur connu n'est pas coche d'office : il faut d'abord dire
            // chez qui l'acheter.
            retenue: !!ligne.fournisseur,
            quantite: ligne.quantiteProposee ?? null,
            prix: ligne.prixAchat ?? null,
            idFournisseur: ligne.fournisseur?.id ?? null,
          })),
        );
        this.chargement.set(false);
      },
      error: (echec: unknown) => {
        this.chargement.set(false);
        this.erreur.set(messageDErreur(echec, 'La proposition n’a pas pu être calculée.'));
      },
    });
  }
}
