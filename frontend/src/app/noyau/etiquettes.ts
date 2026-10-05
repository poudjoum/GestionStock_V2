import { SUPPORT } from './impression';
import { svgDuCode } from './code-barres';

/** Ce qui s'imprime sur une etiquette de rayon. */
export interface Etiquette {
  designation: string;
  /** « Carton de 24 », ou rien a l'unite. */
  conditionnement?: string | null;
  prixTtc: number | null;
  code: string;
}

/**
 * La planche standard du commerce : A4, 3 colonnes sur 7 rangs, etiquettes de 63,5 x 38,1 mm
 * (le format des planches « 21 etiquettes » vendues en papeterie). La mise en page suit ses
 * marges, pour que chaque etiquette tombe dans sa case.
 */
const PLANCHE = `
@page { size: A4; margin: 15.1mm 7.2mm; }
.planche { display: grid; grid-template-columns: repeat(3, 63.5mm); grid-auto-rows: 38.1mm; column-gap: 2.5mm; }
.etiquette { box-sizing: border-box; padding: 2.5mm 3mm; overflow: hidden; display: flex; flex-direction: column;
  justify-content: space-between; font-family: system-ui, sans-serif; color: #000; break-inside: avoid; }
.etiquette__nom { font-size: 9pt; font-weight: 600; line-height: 1.15; max-height: 2.3em; overflow: hidden; }
.etiquette__cond { font-size: 7.5pt; }
.etiquette__bas { display: flex; align-items: flex-end; justify-content: space-between; gap: 2mm; }
.etiquette__prix { font-size: 13pt; font-weight: 800; white-space: nowrap; }
.etiquette svg { width: 34mm; height: auto; }
`;

function echapper(texte: string): string {
  return texte.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]!);
}

/**
 * Imprime une planche d'etiquettes. Meme mecanique que le ticket : une copie sous `body`, seule
 * visible a l'impression, et la regle de page posee le temps d'imprimer.
 */
export function imprimerEtiquettes(etiquette: Etiquette, nombre = 21): void {
  const une = `
    <div class="etiquette">
      <div>
        <div class="etiquette__nom">${echapper(etiquette.designation)}</div>
        ${etiquette.conditionnement ? `<div class="etiquette__cond">${echapper(etiquette.conditionnement)}</div>` : ''}
      </div>
      <div class="etiquette__bas">
        ${svgDuCode(etiquette.code, 0.28, 13)}
        ${etiquette.prixTtc != null ? `<span class="etiquette__prix">${Math.round(etiquette.prixTtc).toLocaleString('fr-FR')} F</span>` : ''}
      </div>
    </div>`;

  const support = document.createElement('div');
  support.className = SUPPORT;
  support.innerHTML = `<div class="planche">${une.repeat(nombre)}</div>`;
  document.body.appendChild(support);

  const regle = document.createElement('style');
  regle.media = 'print';
  regle.textContent = PLANCHE;
  document.head.appendChild(regle);

  const nettoyer = () => {
    support.remove();
    regle.remove();
    window.removeEventListener('afterprint', nettoyer);
  };
  window.addEventListener('afterprint', nettoyer);
  window.print();
  setTimeout(nettoyer, 1000);
}
