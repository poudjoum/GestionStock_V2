import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { Logo } from './logo';

/**
 * Le cadre des pages ou l'on n'est pas encore entre : la connexion, le premier mot de passe.
 *
 * C'est le premier ecran que voit un commercant, et il ressemblait a une maquette — un formulaire
 * nu sous une icone de carton. A gauche, ce qu'est GestionStock ; a droite, le formulaire. Sur
 * telephone, la presentation se resserre en un bandeau au-dessus du formulaire : on est la pour
 * se connecter, pas pour lire.
 */
@Component({
  selector: 'gs-cadre-connexion',
  imports: [MatIconModule, Logo],
  template: `
    <aside class="presentation">
      <gs-logo [taille]="40" />
      <div class="accroche">
        <p class="titre gs-titre">Le stock, la caisse et la fidélité de votre magasin, au même endroit.</p>
        <ul>
          <li><mat-icon aria-hidden="true">point_of_sale</mat-icon><span><b>Vendez même sans internet.</b> La caisse garde ses ventes et les envoie au retour du réseau.</span></li>
          <li><mat-icon aria-hidden="true">inventory_2</mat-icon><span><b>Sachez quoi recommander.</b> Les ruptures et les seuils s’affichent avant que le rayon soit vide.</span></li>
          <li><mat-icon aria-hidden="true">loyalty</mat-icon><span><b>Faites revenir vos clients.</b> Campagnes, points et bons d’achat, dans leur téléphone.</span></li>
        </ul>
      </div>
      <p class="pied">GestionStock · Jumpytech</p>
    </aside>

    <main class="formulaire">
      <div class="contenu">
        <header>
          <h1 class="gs-titre">{{ titre() }}</h1>
          @if (texte()) {
            <p>{{ texte() }}</p>
          }
        </header>
        <ng-content />
      </div>
    </main>
  `,
  host: { class: 'gs-cadre-connexion' },
  styles: `
    :host {
      display: grid;
      grid-template-columns: minmax(320px, 5fr) 7fr;
      min-height: 100dvh;
      background: var(--gs-fond);
    }
    .presentation {
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      gap: var(--gs-esp-6);
      padding: 40px 48px calc(32px + var(--marge-bas));
      background: var(--gs-menu-fond);
      color: var(--gs-menu-encre);
    }
    .accroche {
      display: grid;
      gap: 28px;
      max-width: 34rem;
    }
    .titre {
      margin: 0;
      font-size: clamp(1.75rem, 2.6vw, 2.4rem);
    }
    ul {
      display: grid;
      gap: 18px;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    li {
      display: grid;
      grid-template-columns: 36px minmax(0, 1fr);
      gap: 12px;
      align-items: start;
      font-size: var(--gs-texte-md);
      line-height: 1.5;
      color: color-mix(in srgb, var(--gs-menu-encre) 82%, transparent);
    }
    li b {
      color: var(--gs-menu-encre);
      font-weight: 600;
    }
    li mat-icon {
      display: grid;
      place-items: center;
      width: 36px;
      height: 36px;
      font-size: 20px;
      border-radius: 10px;
      background: color-mix(in srgb, var(--gs-menu-encre) 12%, transparent);
    }
    .pied {
      margin: 0;
      font-size: var(--gs-texte-xs);
      color: color-mix(in srgb, var(--gs-menu-encre) 60%, transparent);
    }
    .formulaire {
      display: grid;
      place-items: center;
      padding: 40px 24px calc(40px + var(--marge-bas));
    }
    .contenu {
      width: 100%;
      max-width: 400px;
      display: grid;
      gap: var(--gs-esp-5);
    }
    header {
      display: grid;
      gap: 6px;
    }
    h1 {
      margin: 0;
      font-size: var(--gs-titre-page);
      color: var(--gs-encre);
    }
    header p {
      margin: 0;
      font-size: var(--gs-texte-md);
      line-height: 1.5;
      color: var(--gs-encre-2);
    }
    @media (max-width: 860px) {
      :host {
        grid-template-columns: minmax(0, 1fr);
        grid-template-rows: auto 1fr;
      }
      .presentation {
        padding: calc(20px + var(--marge-haut)) 20px 20px;
        gap: 14px;
      }
      .accroche {
        gap: 0;
      }
      .titre {
        font-size: 1.25rem;
      }
      ul,
      .pied {
        display: none;
      }
      .formulaire {
        place-items: start center;
        padding-top: 28px;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CadreConnexion {
  readonly titre = input.required<string>();
  readonly texte = input<string | null>(null);
}
