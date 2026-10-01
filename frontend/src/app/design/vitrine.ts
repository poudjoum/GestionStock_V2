import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { EnTetePage } from './en-tete-page';
import { EtatVide } from './etat-vide';
import { Section } from './section';
import { OptionSelecteur, Selecteur } from './selecteur';
import { Statut } from './statut';
import { Tuile } from './tuile';

type Periode = 'jour' | 'semaine' | 'mois';
type EtatCommande = 'tout' | 'preparation' | 'recevoir';

/** Les teintes montrees dans la vitrine, avec le jeton qui les porte et ce a quoi elles servent. */
const TEINTES = [
  { jeton: '--gs-vert-900', nom: 'Vert profond', role: 'Menu latéral, titres de marque' },
  { jeton: '--gs-vert-700', nom: 'Vert marque', role: 'Action principale, sélection' },
  { jeton: '--gs-vert-100', nom: 'Vert clair', role: 'Fond d’élément actif' },
  { jeton: '--gs-petrole-700', nom: 'Bleu-pétrole', role: 'Fidélité, campagnes, promotions' },
  { jeton: '--gs-succes', nom: 'Réussi', role: 'Réglée, suffisant' },
  { jeton: '--gs-alerte', nom: 'Attention', role: 'Sous le seuil, partielle' },
  { jeton: '--gs-danger', nom: 'Bloquant', role: 'Rupture, impayé' },
  { jeton: '--gs-encre', nom: 'Encre', role: 'Texte principal' },
  { jeton: '--gs-encre-2', nom: 'Encre 2', role: 'Texte secondaire' },
  { jeton: '--gs-encre-3', nom: 'Encre 3', role: 'Étiquettes, aides' },
];

/**
 * La vitrine du design system : chaque composant, dans chacun de ses etats, sur les chiffres
 * d'un vrai magasin.
 *
 * Elle sert a juger un composant avant de l'employer, et a verifier d'un coup d'oeil qu'un
 * changement de jeton n'a rien casse — en clair et en sombre. Hors du menu : on y vient en tapant
 * `/design`.
 */
@Component({
  selector: 'app-vitrine',
  imports: [DecimalPipe, MatButtonModule, MatIconModule, EnTetePage, EtatVide, Section, Selecteur, Statut, Tuile],
  templateUrl: './vitrine.html',
  styles: `
    :host {
      display: block;
      color: var(--gs-encre);
    }
    .page {
      max-width: 1200px;
      margin: 0 auto;
      padding: var(--gs-esp-5) var(--gs-esp-4) var(--gs-esp-6);
      display: grid;
      gap: var(--gs-esp-4);
    }
    .grille {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: var(--gs-esp-3);
    }
    .rang {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--gs-esp-2);
    }
    .teinte {
      display: grid;
      border: 1px solid var(--gs-trait);
      border-radius: 10px;
      overflow: hidden;
      background: var(--gs-surface);
    }
    .teinte .echantillon {
      height: 56px;
    }
    .teinte .info {
      display: grid;
      gap: 2px;
      padding: 8px 10px;
      font-size: var(--gs-texte-xs);
    }
    .teinte b {
      font-size: var(--gs-texte-sm);
    }
    .specimen {
      display: grid;
      gap: var(--gs-esp-3);
    }
    .specimen > div {
      display: grid;
      grid-template-columns: 140px minmax(0, 1fr);
      gap: var(--gs-esp-3);
      align-items: baseline;
    }
    @media (max-width: 560px) {
      .specimen > div {
        grid-template-columns: minmax(0, 1fr);
      }
    }
    .tableau {
      overflow-x: auto;
      border: 1px solid var(--gs-trait);
      border-radius: var(--gs-r-carte);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Vitrine {
  protected readonly teintes = TEINTES;

  protected readonly periode = signal<Periode>('jour');
  protected readonly periodes: OptionSelecteur<Periode>[] = [
    { valeur: 'jour', libelle: 'Aujourd’hui' },
    { valeur: 'semaine', libelle: '7 jours' },
    { valeur: 'mois', libelle: '30 jours' },
  ];

  protected readonly commandes = signal<EtatCommande>('tout');
  protected readonly etatsCommande: OptionSelecteur<EtatCommande>[] = [
    { valeur: 'tout', libelle: 'Tout', compteur: 2 },
    { valeur: 'preparation', libelle: 'En préparation', compteur: 2 },
    { valeur: 'recevoir', libelle: 'À recevoir', compteur: 0 },
  ];

  protected readonly articles = [
    { code: 'PEI05C', designation: 'Peinture façade crème — 5 L', stock: 0, seuil: 10, prix: 7500 },
    { code: 'NIV60', designation: 'Niveau à bulle aluminium 60 cm', stock: 1, seuil: 5, prix: 6000 },
    { code: 'SABLE', designation: 'Sable de rivière (brouette)', stock: 4, seuil: 20, prix: 1500 },
    { code: 'CIM50', designation: 'Sac de ciment CPJ 42,5 — 50 kg', stock: 118, seuil: 40, prix: 4600 },
  ];

  protected statutDe(stock: number, seuil: number): 'danger' | 'alerte' | 'ok' {
    return stock <= 0 ? 'danger' : stock < seuil ? 'alerte' : 'ok';
  }
}
