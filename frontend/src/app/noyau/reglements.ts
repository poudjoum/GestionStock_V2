import type { components } from '../api/schema';

export type ReglementDto = components['schemas']['ReglementDto'];
export type ModeReglement = NonNullable<ReglementDto['mode']>;

/**
 * Les moyens par lesquels l'argent entre, dits comme on les dit au comptoir.
 *
 * Une seule table, dans le noyau, parce que trois ecrans la lisent : le comptoir au moment
 * d'encaisser, la liste des factures au moment de reprendre un encaissement, et le ticket qui
 * imprime le moyen employe. Un moyen de paiement qui change de nom d'un ecran a l'autre est un
 * defaut, pas une nuance.
 */
export const MODES_DE_REGLEMENT: { valeur: ModeReglement; libelle: string; icone: string }[] = [
  { valeur: 'ESPECES', libelle: 'Espèces', icone: 'payments' },
  { valeur: 'MOBILE_MONEY', libelle: 'Mobile Money', icone: 'smartphone' },
  { valeur: 'VIREMENT', libelle: 'Virement', icone: 'account_balance' },
  { valeur: 'CHEQUE', libelle: 'Chèque', icone: 'receipt' },
  { valeur: 'AUTRE', libelle: 'Autre', icone: 'more_horiz' },
];

export function libelleDuMode(mode: string | undefined): string {
  return MODES_DE_REGLEMENT.find((m) => m.valeur === mode)?.libelle ?? mode ?? '—';
}
