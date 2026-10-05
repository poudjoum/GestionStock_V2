import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { EnTetePage, EtatVide, Statut } from '../design';
import { messageDErreur } from '../noyau/erreurs';
import { SiteDto, Sites, TypeSite } from '../noyau/sites';

interface Saisie {
  id: number | null;
  nom: string;
  type: TypeSite;
  adresse: string;
  telephone: string;
}

function vide(): Saisie {
  return { id: null, nom: '', type: 'MAGASIN', adresse: '', telephone: '' };
}

/**
 * Les sites de l'entreprise : ses magasins et ses entrepots.
 *
 * Ecran de gerant, ouvert rarement — on ouvre un magasin une fois par an. Il dit surtout ce qu'un
 * site fait : un magasin vend, un entrepot stocke et livre. Fermer un site qui a encore du stock
 * est refuse par le serveur, et c'est son message qu'on montre : il dit quoi faire.
 */
@Component({
  selector: 'app-sites',
  imports: [
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    EnTetePage,
    EtatVide,
    Statut,
  ],
  template: `
    @let s = saisie();
    <div class="mx-auto grid max-w-6xl gap-4" [class]="volet() ? 'xl:grid-cols-[1fr_400px]' : ''">
      <section class="min-w-0">
        <gs-en-tete-page titre="Sites" sousTitre="Vos magasins vendent ; vos entrepôts stockent et livrent les magasins et leurs clients.">
          <button mat-flat-button (click)="nouveau()">
            <mat-icon>add</mat-icon> Nouveau site
          </button>
        </gs-en-tete-page>

        @if (chargement()) {
          <div class="grid gap-2">
            @for (i of [1, 2, 3]; track i) {
              <div class="squelette h-20"></div>
            }
          </div>
        } @else if (erreur(); as m) {
          <p class="message-erreur" role="alert"><mat-icon aria-hidden="true">error_outline</mat-icon><span>{{ m }}</span></p>
        } @else {
          <ul class="grid list-none gap-2 p-0 md:grid-cols-2">
            @for (site of ouverts(); track site.id) {
              <li>
                <button type="button" class="carte carte-cliquable flex w-full items-start gap-3 p-4 text-left"
                        style="color: var(--mat-sys-on-surface)"
                        [style.outline]="s.id === site.id ? '2px solid var(--mat-sys-primary)' : ''"
                        (click)="modifier(site)">
                  <mat-icon class="mt-0.5 shrink-0 opacity-70" aria-hidden="true">
                    {{ site.type === 'ENTREPOT' ? 'warehouse' : 'storefront' }}
                  </mat-icon>
                  <span class="min-w-0 flex-1">
                    <span class="block truncate font-medium">{{ site.nom }}</span>
                    <span class="block truncate text-xs opacity-60">
                      {{ site.type === 'ENTREPOT' ? 'Entrepôt' : 'Magasin' }}{{ site.adresse ? ' · ' + site.adresse : '' }}
                    </span>
                  </span>
                  @if (site.principal) {
                    <gs-statut ton="ok" icone="star">Principal</gs-statut>
                  }
                </button>
              </li>
            }
          </ul>
          @if (ouverts().length === 1) {
            <gs-etat-vide class="mt-4" icone="add_business" titre="Un seul site pour l’instant"
                          texte="Ajoutez un second magasin, ou l’entrepôt où vous gardez votre réserve : chaque site aura son stock, et les transferts déplaceront la marchandise de l’un à l’autre." />
          }
          @if (fermes().length) {
            <h2 class="mt-6 text-xs font-medium uppercase tracking-wide opacity-60">Fermés</h2>
            <ul class="mt-2 grid list-none gap-1 p-0">
              @for (site of fermes(); track site.id) {
                <li class="text-sm opacity-60">{{ site.nom }} — {{ site.type === 'ENTREPOT' ? 'entrepôt' : 'magasin' }}</li>
              }
            </ul>
          }
        }
      </section>

      @if (volet()) {
        <aside class="min-w-0">
          <div class="carte xl:sticky xl:top-0">
            <header class="flex items-center gap-2 border-b px-4 py-3" style="border-color: var(--mat-sys-outline-variant)">
              <span class="flex-1 font-medium">{{ s.id ? 'Modifier le site' : 'Nouveau site' }}</span>
              <button mat-icon-button (click)="fermerVolet()" aria-label="Fermer"><mat-icon>close</mat-icon></button>
            </header>
            <div class="grid gap-4 px-4 py-4">
              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>Nom</mat-label>
                <input matInput maxlength="80" [ngModel]="s.nom" (ngModelChange)="champ('nom', $event)"
                       placeholder="Dépôt Bonabéri" />
              </mat-form-field>

              <div>
                <p class="mb-1 text-xs font-medium uppercase tracking-wide opacity-60">Ce qu’il fait</p>
                <mat-button-toggle-group [value]="s.type" (change)="champ('type', $event.value)"
                                         [disabled]="estPrincipal()" aria-label="Type de site">
                  <mat-button-toggle value="MAGASIN"><mat-icon>storefront</mat-icon> Magasin</mat-button-toggle>
                  <mat-button-toggle value="ENTREPOT"><mat-icon>warehouse</mat-icon> Entrepôt</mat-button-toggle>
                </mat-button-toggle-group>
                <p class="mt-1 text-xs opacity-70">
                  @if (estPrincipal()) {
                    Le site principal reste un magasin : il vend pour les comptes sans site attribué.
                  } @else if (s.type === 'ENTREPOT') {
                    Il ne vend pas au comptoir : il livre les magasins par transfert, et leurs clients sur commande.
                  } @else {
                    Il vend au comptoir, avec sa caisse.
                  }
                </p>
              </div>

              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>Adresse</mat-label>
                <input matInput [ngModel]="s.adresse" (ngModelChange)="champ('adresse', $event)" />
              </mat-form-field>
              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>Téléphone</mat-label>
                <input matInput inputmode="tel" [ngModel]="s.telephone" (ngModelChange)="champ('telephone', $event)" />
              </mat-form-field>

              @if (erreurVolet(); as m) {
                <p class="message-erreur" role="alert"><mat-icon aria-hidden="true">error_outline</mat-icon><span>{{ m }}</span></p>
              }

              <button mat-flat-button class="!h-12" [disabled]="!s.nom.trim() || envoi()" (click)="enregistrer()">
                {{ envoi() ? 'Enregistrement…' : s.id ? 'Enregistrer' : 'Créer le site' }}
              </button>
              @if (s.id && !estPrincipal()) {
                <button mat-button (click)="fermerSite()" [disabled]="envoi()">Fermer ce site</button>
              }
            </div>
          </div>
        </aside>
      }
    </div>
  `,
})
export class SitesEcran implements OnInit {
  private readonly service = inject(Sites);
  private readonly snack = inject(MatSnackBar);

  protected readonly liste = signal<SiteDto[]>([]);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);
  protected readonly volet = signal(false);
  protected readonly saisie = signal<Saisie>(vide());
  protected readonly envoi = signal(false);
  protected readonly erreurVolet = signal<string | null>(null);

  protected readonly ouverts = computed(() => this.liste().filter((s) => s.actif !== false));
  protected readonly fermes = computed(() => this.liste().filter((s) => s.actif === false));
  protected readonly estPrincipal = computed(() =>
    this.liste().some((s) => s.id === this.saisie().id && s.principal),
  );

  ngOnInit(): void {
    this.charger();
  }

  protected nouveau(): void {
    this.saisie.set(vide());
    this.erreurVolet.set(null);
    this.volet.set(true);
  }

  protected modifier(site: SiteDto): void {
    this.saisie.set({
      id: site.id ?? null,
      nom: site.nom ?? '',
      type: site.type ?? 'MAGASIN',
      adresse: site.adresse ?? '',
      telephone: site.telephone ?? '',
    });
    this.erreurVolet.set(null);
    this.volet.set(true);
  }

  protected fermerVolet(): void {
    this.volet.set(false);
  }

  protected champ<K extends keyof Saisie>(cle: K, valeur: Saisie[K]): void {
    this.saisie.update((s) => ({ ...s, [cle]: valeur }));
  }

  protected enregistrer(): void {
    const s = this.saisie();
    const corps: SiteDto = {
      id: s.id ?? undefined,
      nom: s.nom.trim(),
      type: s.type,
      adresse: s.adresse.trim() || undefined,
      telephone: s.telephone.trim() || undefined,
    };
    this.envoi.set(true);
    this.erreurVolet.set(null);
    (s.id ? this.service.modifier(corps) : this.service.creer(corps)).subscribe({
      next: (site) => {
        this.envoi.set(false);
        this.snack.open(s.id ? 'Site modifié.' : `« ${site.nom} » est ouvert.`, 'Fermer', { duration: 3000 });
        this.volet.set(false);
        this.charger();
        // Le selecteur de l'en-tete le propose aussitot.
        this.service.charger().subscribe({ error: () => undefined });
      },
      error: (echec: unknown) => {
        this.envoi.set(false);
        this.erreurVolet.set(messageDErreur(echec, 'Le site n’a pas pu être enregistré.'));
      },
    });
  }

  protected fermerSite(): void {
    const id = this.saisie().id;
    if (!id) {
      return;
    }
    this.envoi.set(true);
    this.erreurVolet.set(null);
    this.service.fermer(id).subscribe({
      next: () => {
        this.envoi.set(false);
        this.snack.open('Site fermé.', 'Fermer', { duration: 3000 });
        this.volet.set(false);
        this.charger();
        this.service.charger().subscribe({ error: () => undefined });
      },
      error: (echec: unknown) => {
        this.envoi.set(false);
        // « Le site a encore du stock sur 3 article(s) — transférez-le… » : le serveur dit quoi faire.
        this.erreurVolet.set(messageDErreur(echec, 'Le site n’a pas pu être fermé.'));
      },
    });
  }

  private charger(): void {
    this.chargement.set(this.liste().length === 0);
    this.erreur.set(null);
    this.service.tous().subscribe({
      next: (sites) => {
        this.liste.set(sites);
        this.chargement.set(false);
      },
      error: (echec: unknown) => {
        this.chargement.set(false);
        this.erreur.set(messageDErreur(echec, 'Les sites n’ont pas pu être chargés.'));
      },
    });
  }
}
