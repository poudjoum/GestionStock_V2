import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Entreprise } from '../noyau/entreprise';
import { messageDErreur } from '../noyau/erreurs';
import { preparerLeLogo } from './logo';
import { Ticket } from '../ticket/ticket';
import type { EntrepriseDto } from '../noyau/api';
import type { FactureDto } from '../comptoir/comptoir.service';

/**
 * Une vente imaginaire, pour l'apercu.
 *
 * Deux articles et un taux : de quoi voir tomber l'en-tete, le detail, la TVA et le total sans
 * avoir a vendre quoi que ce soit. Les montants sont ronds pour qu'on regarde la mise en page et
 * non les chiffres.
 */
const VENTE_POUR_VOIR: FactureDto = {
  numero: 'F-2026-0001',
  dateEmission: new Date().toISOString(),
  totalHt: 21_500,
  totalTva: 4_139,
  totalTtc: 25_639,
  resteAPayer: 0,
  montantRegle: 25_639,
  tvaApplicable: true,
  annulee: false,
  lignes: [
    {
      id: 1,
      designation: 'Ciment 50 kg',
      quantite: 2,
      prixUnitaireHt: 10_000,
      tauxTva: 19.25,
      montantHt: 20_000,
    },
    {
      id: 2,
      designation: 'Fil à plomb',
      quantite: 1,
      prixUnitaireHt: 1_500,
      tauxTva: 19.25,
      montantHt: 1_500,
    },
  ],
};

/**
 * L'identite du magasin : ce que le ticket de caisse imprime en en-tete.
 *
 * Elle se fixait a l'inscription et ne se corrigeait plus qu'en base. Un magasin change
 * d'adresse, de numero, et obtient son NIU quelques mois apres avoir ouvert.
 *
 * L'apercu a droite n'est pas une illustration : c'est le composant du vrai ticket, alimente par
 * une vente imaginaire. Ce qu'on voit en tapant est exactement ce qui sortira du rouleau — une
 * maquette separee finirait par mentir, et l'on ne s'en apercevrait qu'au comptoir.
 */
@Component({
  selector: 'app-identite-du-magasin',
  imports: [
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSlideToggleModule,
    Ticket,
  ],
  templateUrl: './identite-du-magasin.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IdentiteDuMagasin implements OnInit {
  private readonly entreprise = inject(Entreprise);
  private readonly snack = inject(MatSnackBar);

  protected readonly chargement = signal(true);
  protected readonly envoi = signal(false);
  protected readonly erreur = signal<string | null>(null);

  /** L'identite en cours de saisie. Un brouillon : rien n'est envoye avant « Enregistrer ». */
  protected readonly brouillon = signal<EntrepriseDto>({});

  protected readonly apercu = VENTE_POUR_VOIR;

  /**
   * L'entreprise telle que l'apercu doit la voir.
   *
   * Le brouillon est un objet remplace a chaque frappe, ce qui suffit a rafraichir le ticket
   * malgre `OnPush` : c'est un signal, pas une mutation en place.
   */
  protected readonly magasin = computed(() => this.brouillon());

  ngOnInit(): void {
    this.entreprise.charger().subscribe({
      next: (identite) => {
        this.brouillon.set({ ...identite });
        this.chargement.set(false);
      },
      error: (echec: unknown) => {
        this.erreur.set(messageDErreur(echec, 'L’identité du magasin n’a pas pu être lue.'));
        this.chargement.set(false);
      },
    });
  }

  /** Change un champ du brouillon en remplacant l'objet, jamais en le modifiant sur place. */
  protected changer<C extends keyof EntrepriseDto>(champ: C, valeur: EntrepriseDto[C]): void {
    this.brouillon.update((identite) => ({ ...identite, [champ]: valeur }));
  }

  protected changerAdresse(champ: 'adresse1' | 'ville' | 'pays', valeur: string): void {
    this.brouillon.update((identite) => ({
      ...identite,
      adresse: { ...identite.adresse, [champ]: valeur },
    }));
  }

  protected async choisirLeLogo(evenement: Event): Promise<void> {
    const champ = evenement.target as HTMLInputElement;
    const fichier = champ.files?.[0];
    // Le champ est remis a zero tout de suite : sans cela, rechoisir le meme fichier apres
    // l'avoir retire ne declencherait aucun evenement.
    champ.value = '';
    if (!fichier) {
      return;
    }
    this.erreur.set(null);
    try {
      this.changer('logo', await preparerLeLogo(fichier));
    } catch (echec: unknown) {
      this.erreur.set(echec instanceof Error ? echec.message : 'Cette image n’a pas pu être lue.');
    }
  }

  protected retirerLeLogo(): void {
    this.changer('logo', undefined);
  }

  protected enregistrer(): void {
    if (this.envoi()) {
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    this.entreprise.enregistrer(this.brouillon()).subscribe({
      next: (enregistree) => {
        this.brouillon.set({ ...enregistree });
        this.envoi.set(false);
        this.snack.open('Identité enregistrée — les prochains tickets la portent.', 'Fermer', {
          duration: 4000,
        });
      },
      error: (echec: unknown) => {
        this.envoi.set(false);
        // Le message du serveur, tel quel : il nomme le champ en cause, ce qu'un texte generique
        // ne ferait pas.
        this.erreur.set(
          messageDErreur(echec, 'L’identité du magasin n’a pas pu être enregistrée.'),
        );
      },
    });
  }
}
