import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Accueil, EtatDeCaisseDto, EtatDuStockDto, FactureDto } from './accueil.service';
import { Sites } from '../noyau/sites';
import { Session } from '../noyau/session';
import type { LigneInventaireDto } from '../noyau/api';
import { libelleDuMode } from '../noyau/reglements';
import { statutDeFacture, statutDuStock } from '../noyau/statuts';
import type { CampagneDuJour } from '../comptoir/prix-promotionnel';
import { EnTetePage, EtatVide, Section, Statut, Tuile } from '../design';

/**
 * Ce qu'on voit en arrivant : ce qu'a encaisse la caisse, ce qui attend d'etre encaisse, ce qui
 * manque en rayon, et ce que vaut le stock.
 *
 * Chaque bloc ne se demande que si le role le voit, et se remplit des que sa reponse arrive, sans
 * attendre les autres. Un chiffre qui manque se dit (« Coûts à saisir ») au lieu de s'afficher a
 * zero : un « 0 F » de valeur de stock se lisait comme un stock qui ne vaut rien.
 */
@Component({
  selector: 'app-tableau-de-bord',
  imports: [
    DatePipe,
    DecimalPipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    EnTetePage,
    EtatVide,
    Section,
    Statut,
    Tuile,
  ],
  templateUrl: './tableau-de-bord.html',
  styleUrl: './tableau-de-bord.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TableauDeBord implements OnInit {
  private readonly service = inject(Accueil);
  private readonly session = inject(Session);
  protected readonly plusieursSites = inject(Sites).plusieurs;

  protected readonly stock = signal<EtatDuStockDto | null>(null);
  protected readonly caisse = signal<EtatDeCaisseDto | null>(null);
  protected readonly alertes = signal<LigneInventaireDto[]>([]);
  protected readonly factures = signal<FactureDto[]>([]);
  protected readonly facturesDues = signal<number | null>(null);
  protected readonly campagnes = signal<CampagneDuJour[]>([]);
  protected readonly chargement = signal(true);

  protected readonly peutVoirLaCaisse = signal(false);
  protected readonly peutVoirLeStock = signal(false);

  protected readonly statutDuStock = statutDuStock;
  protected readonly statutDeFacture = statutDeFacture;
  protected readonly libelleDuMode = libelleDuMode;

  /** Le prenom du compte, et a defaut son identifiant : « Bonsoir Paul », pas « Bonsoir, gerant ». */
  protected readonly prenom = computed(() => {
    const moi = this.session.moi();
    const prenom = moi?.prenoms?.trim().split(/\s+/)[0];
    return prenom || moi?.nom || this.session.username() || '';
  });

  protected readonly titre = computed(() => `${salutation()} ${this.prenom()}`.trim());

  /** La valeur du stock n'a de sens que si les couts sont connus : sinon la tuile le dit. */
  protected readonly valeurDuStock = computed(() => {
    const s = this.stock();
    if (!s) return null;
    const sansCout = s.nombreSansCoutConnu ?? 0;
    return sansCout >= (s.nombreArticles ?? 0) && sansCout > 0 ? null : (s.valeurAuCout ?? 0);
  });

  protected readonly aRecommander = computed(() => {
    const s = this.stock();
    return s ? (s.nombreEnRupture ?? 0) + (s.nombreSousSeuil ?? 0) : null;
  });

  ngOnInit(): void {
    const roles = this.session.roles();
    // Chaque bloc n'est demande que si le role le voit : appeler une route qui rendra 403
    // remplirait la console d'erreurs et n'apprendrait rien a l'utilisateur.
    this.peutVoirLeStock.set(
      roles.some((r) => ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_MAGASINIER', 'ROLE_COMPTABLE'].includes(r)),
    );
    this.peutVoirLaCaisse.set(
      roles.some((r) => ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_CAISSIER', 'ROLE_COMPTABLE'].includes(r)),
    );

    if (this.peutVoirLeStock()) {
      this.service.etatDuStock().subscribe({ next: (e) => this.stock.set(e), error: () => undefined });
      this.service.alertes().subscribe({
        next: (lignes) => this.alertes.set(lignes.slice(0, 6)),
        error: () => undefined,
      });
    }
    if (this.peutVoirLaCaisse()) {
      this.service.etatDeCaisse().subscribe({ next: (e) => this.caisse.set(e), error: () => undefined });
      this.service.dernieresFactures().subscribe({
        next: (page) => this.factures.set(page.content ?? []),
        error: () => undefined,
      });
      this.service.facturesDues().subscribe({
        next: (n) => this.facturesDues.set(n),
        error: () => undefined,
      });
    }
    // La campagne en cours, rappelee en haut : ce qu'on vend moins cher aujourd'hui. Le comptable
    // n'y a pas acces, et n'en a pas l'usage — l'echec est silencieux.
    this.service.campagnesEnCours().subscribe({
      next: (campagnes) => this.campagnes.set(campagnes),
      error: () => undefined,
    });
    // Le squelette disparait vite : chaque bloc se remplit quand sa reponse arrive.
    setTimeout(() => this.chargement.set(false), 400);
  }

  /** La part d'un moyen de paiement dans la recette, pour la barre qui l'accompagne. */
  protected part(total: number | undefined): number {
    const recette = this.caisse()?.total || 1;
    return ((total ?? 0) / recette) * 100;
  }
}

function salutation(): string {
  const heure = new Date().getHours();
  if (heure < 12) return 'Bonjour';
  if (heure < 18) return 'Bonne journée';
  return 'Bonsoir';
}
