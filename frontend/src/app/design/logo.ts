import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Le logo de GestionStock : la caisse de marchandise sur le vert de la marque, et son nom.
 *
 * Ses couleurs sont fixes, et non des jetons : un logo ne change pas avec le theme. Le meme
 * dessin que `public/logo.svg`, le favicon et les icones de l'application installee.
 *
 * ```html
 * <gs-logo />                  <!-- la marque et le nom -->
 * <gs-logo [nom]="false" />    <!-- la marque seule -->
 * ```
 */
@Component({
  selector: 'gs-logo',
  template: `
    <svg viewBox="0 0 32 32" [attr.width]="taille()" [attr.height]="taille()" aria-hidden="true">
      <rect width="32" height="32" rx="8" fill="#1d6b43" />
      <path d="M16 6.5 25.5 11.25 16 16 6.5 11.25Z" fill="#fff" />
      <path d="M6.5 12.75 15.25 17.1V26.5L6.5 22.1Z" fill="#fff" fill-opacity="0.82" />
      <path d="M25.5 12.75 16.75 17.1V26.5L25.5 22.1Z" fill="#fff" fill-opacity="0.6" />
    </svg>
    @if (nom()) {
      <span class="nom">Gestion<b>Stock</b></span>
    }
  `,
  host: {
    class: 'gs-logo',
    role: 'img',
    'aria-label': 'GestionStock',
  },
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      color: inherit;
    }
    svg {
      flex-shrink: 0;
      display: block;
    }
    .nom {
      font: 500 1.125rem / 1 var(--gs-police-titre);
      letter-spacing: -0.01em;
      white-space: nowrap;
    }
    .nom b {
      font-weight: 700;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Logo {
  readonly nom = input(true);
  readonly taille = input(32);
}
