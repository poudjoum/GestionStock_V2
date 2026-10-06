import type { OptionSelecteur } from '../design';

/** Les periodes qu'on choisit d'un geste, et la libre. */
export type Preset = 'jour' | 'semaine' | 'mois' | 'mois-dernier' | 'annee' | 'libre';

export const PRESETS: OptionSelecteur<Preset>[] = [
  { valeur: 'jour', libelle: 'Aujourd’hui' },
  { valeur: 'semaine', libelle: '7 jours' },
  { valeur: 'mois', libelle: 'Ce mois' },
  { valeur: 'mois-dernier', libelle: 'Mois dernier' },
  { valeur: 'annee', libelle: 'Cette année' },
  { valeur: 'libre', libelle: 'Dates…' },
];

/** Le premier et le dernier jour d'une periode predefinie. */
export function bornes(preset: Preset, aujourdhui: Date): [Date, Date] {
  const jour = (d: Date, n: number) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
  switch (preset) {
    case 'jour':
      return [aujourdhui, aujourdhui];
    case 'semaine':
      return [jour(aujourdhui, -6), aujourdhui];
    case 'mois-dernier':
      return [
        new Date(aujourdhui.getFullYear(), aujourdhui.getMonth() - 1, 1),
        new Date(aujourdhui.getFullYear(), aujourdhui.getMonth(), 0),
      ];
    case 'annee':
      return [new Date(aujourdhui.getFullYear(), 0, 1), aujourdhui];
    default:
      return [debutDuMois(aujourdhui), aujourdhui];
  }
}

export function debutDuMois(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), 1);
}

/** AAAA-MM-JJ dans le fuseau de l'appareil : c'est le jour que le gerant a sous les yeux. */
export function iso(d: Date): string {
  const p = (n: number) => `${n}`.padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

export function enDate(cle: string): Date {
  const [a, m, j] = cle.split('-').map(Number);
  return new Date(a, (m ?? 1) - 1, j ?? 1);
}

export function jourLisible(date: string | undefined): string {
  return date ? enDate(date).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' }) : '';
}

/** L'ecart en pourcentage ; nul quand la reference est nulle — « +∞ % » ne dit rien. */
export function ecart(valeur: number | undefined, reference: number | undefined): number | null {
  if (valeur == null || !reference) return null;
  return Math.round(((valeur - reference) / Math.abs(reference)) * 1000) / 10;
}

export function variation(v: number | null): string {
  if (v == null) return '—';
  const signe = v > 0 ? '+' : '';
  return `${signe}${v.toLocaleString('fr-FR', { maximumFractionDigits: 1 })} %`;
}
