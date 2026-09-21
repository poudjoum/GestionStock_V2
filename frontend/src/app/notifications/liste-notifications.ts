import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Notifications } from './notifications.service';
import type { NotificationDto } from '../noyau/api';

/** L'icone qui va avec chaque famille d'alerte. */
const ICONES: Record<string, string> = {
  STOCK_ALERTE: 'inventory_2',
  FACTURE_EMISE: 'receipt_long',
  COMPTE_CREE: 'person_add',
};

@Component({
  selector: 'app-liste-notifications',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatProgressBarModule, MatSlideToggleModule],
  templateUrl: './liste-notifications.html',
})
export class ListeNotifications implements OnInit {
  private readonly service = inject(Notifications);
  private readonly router = inject(Router);

  protected readonly lignes = signal<NotificationDto[]>([]);
  protected readonly chargement = signal(true);
  protected readonly nonLuesSeulement = signal(false);
  protected readonly erreur = signal<string | null>(null);

  ngOnInit(): void {
    this.charger();
  }

  protected basculerLeFiltre(nonLues: boolean): void {
    this.nonLuesSeulement.set(nonLues);
    this.charger();
  }

  protected ouvrir(notification: NotificationDto): void {
    if (!notification.lue && notification.id) {
      this.service.marquerLue(notification.id).subscribe({
        next: () => this.remplacer({ ...notification, lue: true }),
        error: () => undefined,
      });
    }
    if (notification.lien) {
      void this.router.navigateByUrl(notification.lien);
    }
  }

  protected toutMarquerLu(): void {
    this.service.marquerToutesLues().subscribe({
      next: () => this.charger(),
      error: () => undefined,
    });
  }

  protected icone(type: string | undefined): string {
    return (type && ICONES[type]) || 'notifications';
  }

  private charger(): void {
    this.chargement.set(true);
    this.erreur.set(null);
    this.service.lister(this.nonLuesSeulement()).subscribe({
      next: (page) => {
        this.lignes.set(page.content ?? []);
        this.chargement.set(false);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Les notifications n’ont pas pu être chargées.');
      },
    });
  }

  private remplacer(modifiee: NotificationDto): void {
    this.lignes.update((liste) =>
      this.nonLuesSeulement()
        ? liste.filter((n) => n.id !== modifiee.id)
        : liste.map((n) => (n.id === modifiee.id ? modifiee : n)),
    );
  }
}
