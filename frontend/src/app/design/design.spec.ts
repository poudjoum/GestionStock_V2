import { Component, LOCALE_ID, signal } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { OptionSelecteur, Selecteur } from './selecteur';
import { Statut } from './statut';
import { Tuile } from './tuile';

registerLocaleData(localeFr);

@Component({
  imports: [Selecteur],
  template: `<gs-selecteur aria-label="Période" [options]="options" [(valeur)]="choix" />`,
})
class HoteSelecteur {
  readonly choix = signal('jour');
  readonly options: OptionSelecteur<string>[] = [
    { valeur: 'jour', libelle: 'Aujourd’hui' },
    { valeur: 'semaine', libelle: '7 jours', compteur: 4 },
    { valeur: 'mois', libelle: '30 jours' },
  ];
}

@Component({
  imports: [Tuile, Statut],
  template: `
    <gs-tuile id="connue" libelle="Caisse du jour" [valeur]="333739" unite="F" />
    <gs-tuile id="manquante" libelle="Valeur du stock" [valeur]="null" manquant="Coûts à saisir" />
    <gs-tuile id="zero" libelle="À recommander" [valeur]="0" />
    <gs-statut ton="danger" icone="error">Impayée</gs-statut>
  `,
})
class HoteTuiles {}

describe('gs-selecteur', () => {
  let racine: HTMLElement;
  let hote: HoteSelecteur;

  beforeEach(() => {
    const fixture = TestBed.configureTestingModule({
      imports: [HoteSelecteur],
      providers: [{ provide: LOCALE_ID, useValue: 'fr-FR' }],
    }).createComponent(HoteSelecteur);
    fixture.autoDetectChanges();
    racine = fixture.nativeElement;
    hote = fixture.componentInstance;
  });

  const boutons = () => Array.from(racine.querySelectorAll<HTMLButtonElement>('button'));

  it('s’annonce comme un groupe radio, l’option choisie cochée', () => {
    expect(racine.querySelector('gs-selecteur')?.getAttribute('role')).toBe('radiogroup');
    expect(boutons().map((b) => b.getAttribute('aria-checked'))).toEqual(['true', 'false', 'false']);
  });

  it('n’offre qu’une tabulation : l’option choisie', () => {
    expect(boutons().map((b) => b.tabIndex)).toEqual([0, -1, -1]);
  });

  it('change d’option au clic', () => {
    boutons()[2].click();
    expect(hote.choix()).toBe('mois');
  });

  it('parcourt les options aux flèches, en boucle', () => {
    boutons()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
    expect(hote.choix()).toBe('mois');
    boutons()[2].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    expect(hote.choix()).toBe('jour');
  });

  it('affiche le compteur d’une option', () => {
    expect(boutons()[1].textContent).toContain('4');
  });
});

describe('gs-tuile', () => {
  let racine: HTMLElement;

  beforeEach(() => {
    const fixture = TestBed.configureTestingModule({
      imports: [HoteTuiles],
      providers: [{ provide: LOCALE_ID, useValue: 'fr-FR' }],
    }).createComponent(HoteTuiles);
    fixture.detectChanges();
    racine = fixture.nativeElement;
  });

  const texte = (id: string) => racine.querySelector(`#${id} .valeur`)?.textContent?.replace(/\s+/g, ' ').trim();

  it('met le chiffre en forme à la française, avec son unité', () => {
    // Le séparateur de milliers français est une espace fine insécable.
    expect(texte('connue')?.replace(/ /g, ' ')).toBe('333 739F');
  });

  it('dit que la donnée manque au lieu d’afficher zéro', () => {
    expect(texte('manquante')).toBe('Coûts à saisir');
  });

  it('affiche un vrai zéro quand la valeur est zéro', () => {
    expect(texte('zero')).toBe('0');
  });

  it('donne à la pastille une icône et un mot', () => {
    const pastille = racine.querySelector('gs-statut')!;
    expect(pastille.classList).toContain('gs-statut--danger');
    expect(pastille.querySelector('mat-icon')?.textContent).toBe('error');
    expect(pastille.textContent).toContain('Impayée');
  });
});
