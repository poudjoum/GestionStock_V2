import type { components } from '../api/schema';

/**
 * `BON_ACHAT` est ajoute a la main, en attendant la regeneration des types contre l'API deployee.
 */
export type ModeReglement = NonNullable<components['schemas']['ReglementDto']['mode']> | 'BON_ACHAT';
export type ReglementDto = Omit<components['schemas']['ReglementDto'], 'mode'> & {
  mode?: ModeReglement;
};

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

/**
 * Le bon d'achat, a part des autres moyens : il ne se choisit pas dans la liste, il se lit — son
 * code donne son montant. Mais il se nomme comme eux, sur le ticket et dans les factures.
 */
export const BON_ACHAT = { valeur: 'BON_ACHAT' as const, libelle: 'Bon d’achat', icone: 'redeem' };

export function libelleDuMode(mode: string | undefined): string {
  if (mode === BON_ACHAT.valeur) {
    return BON_ACHAT.libelle;
  }
  return MODES_DE_REGLEMENT.find((m) => m.valeur === mode)?.libelle ?? mode ?? '—';
}
