import { Component, ElementRef, OnDestroy, afterNextRender, output, signal, viewChild } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

/** Ce que le navigateur sait lire, quand il le sait : Chrome sur Android, Edge, ChromeOS, macOS. */
interface Detecteur {
  detect(source: CanvasImageSource): Promise<{ rawValue: string }[]>;
}
declare const BarcodeDetector: {
  new (options: { formats: string[] }): Detecteur;
  getSupportedFormats(): Promise<string[]>;
};

/** Les symbologies du commerce, et le QR. */
const FORMATS = ['ean_13', 'ean_8', 'upc_a', 'upc_e', 'itf', 'code_128', 'code_39', 'qr_code'];

/**
 * Le telephone peut-il lire un code a la camera ?
 *
 * Il faut le detecteur du navigateur et une camera — et une page servie en HTTPS, sans quoi la
 * camera est refusee. Sans tout cela, le bouton ne s'affiche pas : un bouton qui ne mene a rien
 * est pire qu'un bouton absent.
 */
export function cameraDisponible(): boolean {
  return (
    typeof window !== 'undefined' &&
    'BarcodeDetector' in window &&
    !!navigator.mediaDevices?.getUserMedia &&
    window.isSecureContext
  );
}

/**
 * Lire un code-barres a la camera, pour qui n'a pas de douchette.
 *
 * Un plein ecran, la camera arriere, un cadre ou viser. Des qu'un code est lu, il est rendu et la
 * camera s'arrete : on scanne un article, pas un rayon entier — le geste suivant se refait.
 *
 * ```html
 * @if (camera) { <gs-scan-camera (lu)="valider($event)" (ferme)="camera = false" /> }
 * ```
 */
@Component({
  selector: 'gs-scan-camera',
  imports: [MatButtonModule, MatIconModule],
  template: `
    <div class="fond" role="dialog" aria-modal="true" aria-label="Scanner à la caméra">
      <video #video autoplay muted playsinline></video>
      <div class="cadre" aria-hidden="true"></div>
      <p class="consigne">
        @if (erreur(); as e) {
          {{ e }}
        } @else {
          Visez le code-barres dans le cadre
        }
      </p>
      <button mat-flat-button class="fermer" (click)="fermer()">
        <mat-icon>close</mat-icon> Fermer
      </button>
    </div>
  `,
  styles: `
    .fond {
      position: fixed;
      inset: 0;
      z-index: 1000;
      background: #000;
      display: grid;
      place-items: center;
    }
    video {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .cadre {
      position: relative;
      width: min(80vw, 420px);
      aspect-ratio: 2 / 1;
      border: 3px solid #fff;
      border-radius: 12px;
      box-shadow: 0 0 0 100vmax rgb(0 0 0 / 0.45);
    }
    .consigne {
      position: absolute;
      top: 12%;
      left: 16px;
      right: 16px;
      margin: 0;
      text-align: center;
      color: #fff;
      font-size: 1rem;
      font-weight: 600;
    }
    .fermer {
      position: absolute;
      bottom: calc(24px + env(safe-area-inset-bottom));
      left: 50%;
      transform: translateX(-50%);
      min-height: 48px;
    }
  `,
  host: { '(document:keydown.escape)': 'fermer()' },
})
export class ScanCamera implements OnDestroy {
  /** Le code lu. La camera est deja arretee quand il part. */
  readonly lu = output<string>();
  readonly ferme = output<void>();

  protected readonly erreur = signal<string | null>(null);
  private readonly video = viewChild.required<ElementRef<HTMLVideoElement>>('video');
  private flux: MediaStream | null = null;
  private actif = true;

  constructor() {
    afterNextRender(() => void this.demarrer());
  }

  private async demarrer(): Promise<void> {
    try {
      this.flux = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: 'environment' } },
        audio: false,
      });
    } catch {
      this.erreur.set('La caméra est refusée : autorisez-la dans les réglages du navigateur.');
      return;
    }
    const video = this.video().nativeElement;
    video.srcObject = this.flux;
    const connus = await BarcodeDetector.getSupportedFormats().catch(() => FORMATS);
    const detecteur = new BarcodeDetector({ formats: FORMATS.filter((f) => connus.includes(f)) });

    // Quatre lectures par seconde : assez pour que le code passe des qu'il est net, sans faire
    // chauffer un telephone d'entree de gamme.
    while (this.actif) {
      if (video.readyState >= 2) {
        const codes = await detecteur.detect(video).catch(() => []);
        const code = codes[0]?.rawValue?.trim();
        if (code && this.actif) {
          navigator.vibrate?.(60);
          this.arreter();
          this.lu.emit(code);
          return;
        }
      }
      await new Promise((r) => setTimeout(r, 250));
    }
  }

  protected fermer(): void {
    this.arreter();
    this.ferme.emit();
  }

  private arreter(): void {
    this.actif = false;
    this.flux?.getTracks().forEach((piste) => piste.stop());
    this.flux = null;
  }

  ngOnDestroy(): void {
    this.arreter();
  }
}
