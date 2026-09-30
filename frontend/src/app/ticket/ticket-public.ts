import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { environnement } from '../../environnements/environnement';
import { codeLisible } from '../noyau/code-ticket';

/** Le ticket tel que le rend `GET /tickets/{code}` : ce que le papier disait, sans plus. */
interface TicketPublic {
  code: string;
  magasin?: string;
  ville?: string;
  date?: string;
  articles: { designation?: string; quantite?: number; montant?: number }[];
  totalTtc?: number;
  points: number;
  fideliteActive: boolean;
  montantParPoint?: number;
  annulee: boolean;
}

/**
 * Ce que voit le client qui scanne le QR de son ticket.
 *
 * Il n'a pas de compte et n'en aura pas besoin : la page se lit sans connexion, depuis son
 * telephone, hors de la coque de l'application. Le magasin et les points en tete — c'est ce
 * qu'il vient chercher —, puis ses articles et le total, pour qu'il reconnaisse son achat.
 *
 * Le jour du jeu, c'est ici que se reclameront les points : la page sait deja ce que vaut le ticket.
 */
@Component({
  selector: 'app-ticket-public',
  imports: [DatePipe, DecimalPipe, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './ticket-public.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TicketPublicPage implements OnInit {
  private readonly http = inject(HttpClient);

  /** Le code, depuis l'adresse `/t/:code`. */
  readonly code = input.required<string>();

  protected readonly ticket = signal<TicketPublic | null>(null);
  protected readonly chargement = signal(true);
  protected readonly introuvable = signal<string | null>(null);

  protected readonly lisible = codeLisible;

  ngOnInit(): void {
    this.http
      .get<TicketPublic>(
        `${environnement.api}/gestiondestock/v1/tickets/${encodeURIComponent(this.code())}`,
      )
      .subscribe({
        next: (ticket) => {
          this.ticket.set(ticket);
          this.chargement.set(false);
        },
        error: (echec: unknown) => {
          this.chargement.set(false);
          this.introuvable.set(
            echec instanceof HttpErrorResponse && echec.status === 404
              ? // Le cas ordinaire d'un 404 : un ticket imprime hors ligne, pas encore arrive.
                'Ce ticket n’est pas encore enregistré. S’il vient d’être imprimé pendant une coupure, il apparaîtra dès que la caisse aura retrouvé le réseau.'
              : 'Le ticket n’a pas pu être chargé. Réessayez dans un instant.',
          );
        },
      });
  }
}
