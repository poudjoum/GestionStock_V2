import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Stock } from './stock.service';
import type { LigneInventaireDto } from '../noyau/api';
import { pastilleDe } from '../noyau/statuts';

/**
 * L'etat du stock, tel qu'on le consulte debout dans les rayons.
 *
 * Une recherche, une liste, rien d'autre. Le magasinier cherche un article precis et veut savoir
 * combien il en reste : lui servir un tableau de valorisation a faire defiler serait lui donner
 * l'inventaire du comptable sur un ecran de cinq pouces.
 *
 * La frappe est temporisee : un caractere par requete ferait huit allers-retours pour « ciment »,
 * ce qui se voit sur une connexion de telephone.
 */
@Component({
  selector: 'app-etat-du-stock',
  imports: [
    DecimalPipe,
    FormsModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  templateUrl: './etat-du-stock.html',
})
export class EtatDuStock implements OnInit {
  private readonly stock = inject(Stock);
  private readonly frappe = new Subject<string>();

  protected readonly recherche = signal('');
  protected readonly alertesSeulement = signal(false);
  protected readonly lignes = signal<LigneInventaireDto[]>([]);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);
  protected readonly total = signal(0);

  constructor() {
    this.frappe
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((q) => this.stock.inventaire(q)),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (page) => this.afficher(page.content ?? [], page.totalElements ?? 0),
        error: () => this.echouer(),
      });
  }

  ngOnInit(): void {
    this.charger();
  }

  protected chercher(q: string): void {
    this.recherche.set(q);
    if (this.alertesSeulement()) {
      // La recherche reprend la main : on ne cherche pas dans une liste deja restreinte sans le
      // dire, cela donnerait des resultats inexplicables.
      this.alertesSeulement.set(false);
    }
    this.chargement.set(true);
    this.frappe.next(q);
  }

  protected basculerAlertes(alertes: boolean): void {
    this.alertesSeulement.set(alertes);
    this.charger();
  }

  protected statut(ligne: LigneInventaireDto) {
    return pastilleDe(ligne);
  }

  /** Une quantite negative se lit d'un coup d'oeil : c'est elle qui demande un comptage. */
  protected estAnormal(ligne: LigneInventaireDto): boolean {
    return ligne.statut === 'NEGATIF';
  }

  private charger(): void {
    this.chargement.set(true);
    this.erreur.set(null);
    if (this.alertesSeulement()) {
      this.stock.alertes().subscribe({
        next: (lignes) => this.afficher(lignes, lignes.length),
        error: () => this.echouer(),
      });
      return;
    }
    this.stock.inventaire(this.recherche()).subscribe({
      next: (page) => this.afficher(page.content ?? [], page.totalElements ?? 0),
      error: () => this.echouer(),
    });
  }

  private afficher(lignes: LigneInventaireDto[], total: number): void {
    this.lignes.set(lignes);
    this.total.set(total);
    this.chargement.set(false);
  }

  private echouer(): void {
    this.chargement.set(false);
    this.erreur.set('Le stock n’a pas pu être chargé.');
  }
}
