import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { EnTetePage, EtatVide, Section, Statut } from '../design';
import { messageDErreur } from '../noyau/erreurs';
import { Demarrage, DemarrageDto, ETAPES, EtapeDto, prochaineEtape } from './demarrage.service';

/**
 * L'assistant de demarrage : les six etapes qui menent un commerce de son inscription a sa
 * premiere vente.
 *
 * Chaque etape renvoie vers l'ecran qui existe deja pour la faire — l'assistant ne refait aucun
 * formulaire, il montre le chemin et dit ou l'on en est. L'avancement vient du serveur, qui le lit
 * dans les donnees : revenir sur cette page apres avoir ajoute une categorie suffit a la voir faite.
 */
@Component({
  selector: 'app-assistant-demarrage',
  imports: [RouterLink, MatButtonModule, MatIconModule, EnTetePage, EtatVide, Section, Statut],
  templateUrl: './assistant.html',
  styleUrl: './assistant.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AssistantDemarrage implements OnInit {
  private readonly service = inject(Demarrage);

  protected readonly etat = signal<DemarrageDto | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly enregistrement = signal(false);
  protected readonly ETAPES = ETAPES;

  protected readonly obligatoires = computed(
    () => this.etat()?.etapes.filter((e) => !e.facultative) ?? [],
  );
  protected readonly faites = computed(() => this.obligatoires().filter((e) => e.faite).length);
  protected readonly avancement = computed(() => {
    const total = this.obligatoires().length;
    return total ? (this.faites() / total) * 100 : 0;
  });
  protected readonly prochaine = computed(() => {
    const etat = this.etat();
    return etat ? prochaineEtape(etat) : null;
  });

  ngOnInit(): void {
    this.service.etat().subscribe({
      next: (etat) => this.etat.set(etat),
      error: (echec: unknown) =>
        this.erreur.set(messageDErreur(echec, 'L’avancement du magasin n’a pas pu être lu.')),
    });
  }

  protected numero(etape: EtapeDto): number {
    return (this.etat()?.etapes.indexOf(etape) ?? 0) + 1;
  }

  protected basculerAffichage(): void {
    const etat = this.etat();
    if (!etat) return;
    this.enregistrement.set(true);
    this.service.masquer(!etat.masque).subscribe({
      next: (nouvel) => {
        this.etat.set(nouvel);
        this.enregistrement.set(false);
      },
      error: (echec: unknown) => {
        this.enregistrement.set(false);
        this.erreur.set(messageDErreur(echec, 'Le choix n’a pas pu être enregistré.'));
      },
    });
  }
}
