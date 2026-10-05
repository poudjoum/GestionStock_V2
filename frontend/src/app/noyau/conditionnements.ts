import type { components } from '../api/schema';
import type { ArticleDto } from './api';

type Schemas = components['schemas'];

export type ConditionnementDto = Schemas['ConditionnementDto'];
export type CodeBarresDto = Schemas['CodeBarresDto'];
export type ResultatScanDto = Schemas['ResultatScanDto'];
export type UniteMesure = NonNullable<ArticleDto['uniteBase']>;
export type TypeCodeBarres = NonNullable<CodeBarresDto['type']>;

/** Les unites proposees a la fiche article, dans l'ordre ou on les cherche. */
export const UNITES: { valeur: UniteMesure; libelle: string; symbole: string; fractionnable: boolean }[] = [
  { valeur: 'PIECE', libelle: 'À la pièce', symbole: 'pièce', fractionnable: false },
  { valeur: 'KG', libelle: 'Au kilo', symbole: 'kg', fractionnable: true },
  { valeur: 'LITRE', libelle: 'Au litre', symbole: 'L', fractionnable: true },
  { valeur: 'METRE', libelle: 'Au mètre', symbole: 'm', fractionnable: true },
  { valeur: 'M2', libelle: 'Au mètre carré', symbole: 'm²', fractionnable: true },
  { valeur: 'M3', libelle: 'Au mètre cube', symbole: 'm³', fractionnable: true },
];

function unite(article: Pick<ArticleDto, 'uniteBase'> | null | undefined) {
  return UNITES.find((u) => u.valeur === (article?.uniteBase ?? 'PIECE')) ?? UNITES[0];
}

/** « pièce », « kg » : ce qu'on ecrit apres une quantite d'unites de base. */
export function symbole(article: Pick<ArticleDto, 'uniteBase'> | null | undefined): string {
  return unite(article).symbole;
}

/** L'unite accordee au nombre qui la precede : « 1 pièce », « 245 pièces », « 2,5 kg ». */
export function unites(article: Pick<ArticleDto, 'uniteBase'> | null | undefined, quantite: number | null | undefined): string {
  const s = symbole(article);
  return s === 'pièce' && Math.abs(quantite ?? 0) >= 2 ? 'pièces' : s;
}

/** Se vend-il au detail de son unite — 1,250 kg — ou seulement en nombre entier ? */
export function fractionnable(article: Pick<ArticleDto, 'uniteBase'> | null | undefined): boolean {
  return unite(article).fractionnable;
}

/** Les conditionnements qu'on peut vendre au comptoir, du plus petit au plus grand. */
export function vendables(article: ArticleDto | null | undefined): ConditionnementDto[] {
  return (article?.conditionnements ?? []).filter((c) => c.actif !== false && c.vendable !== false);
}

/** Les conditionnements dans lesquels on peut acheter. */
export function achetables(article: ArticleDto | null | undefined): ConditionnementDto[] {
  return (article?.conditionnements ?? []).filter((c) => c.actif !== false && c.achetable !== false);
}

/** Le nom d'une unite de la ligne : « Carton de 24 », ou l'unite de base de l'article. */
export function libelleDeLigne(article: ArticleDto, conditionnement: ConditionnementDto | null | undefined): string {
  return conditionnement?.libelle ?? symbole(article);
}

/**
 * Une quantite d'unites de base, lue dans les conditionnements de l'article : 293 bouteilles
 * deviennent « 12 cartons de 24 + 5 pièces ». Le plus grand conditionnement d'abord — c'est
 * ainsi qu'on compte une reserve —, et seulement si la quantite en contient au moins un.
 */
export function enConditionnements(
  article: Pick<ArticleDto, 'uniteBase' | 'conditionnements'>,
  quantite: number | null | undefined,
): string | null {
  if (quantite == null || quantite <= 0) {
    return null;
  }
  const grands = (article.conditionnements ?? [])
    .filter((c) => c.actif !== false && (c.quantiteUnites ?? 0) > 1)
    .sort((a, b) => (b.quantiteUnites ?? 0) - (a.quantiteUnites ?? 0));
  const plusGrand = grands[0];
  if (!plusGrand || quantite < (plusGrand.quantiteUnites ?? 0)) {
    return null;
  }
  const contenance = plusGrand.quantiteUnites!;
  const nombre = Math.floor(quantite / contenance);
  const reste = Math.round((quantite - nombre * contenance) * 1000) / 1000;
  const partie = `${nombre.toLocaleString('fr-FR')} × ${plusGrand.libelle}`;
  return reste > 0 ? `${partie} + ${reste.toLocaleString('fr-FR')} ${unites(article, reste)}` : partie;
}

export const TYPES_DE_CODE: { valeur: TypeCodeBarres; libelle: string }[] = [
  { valeur: 'EAN13', libelle: 'EAN-13' },
  { valeur: 'EAN8', libelle: 'EAN-8' },
  { valeur: 'UPCA', libelle: 'UPC-A' },
  { valeur: 'ITF14', libelle: 'ITF-14 (carton)' },
  { valeur: 'CODE128', libelle: 'Code 128' },
  { valeur: 'QR', libelle: 'QR code' },
  { valeur: 'INTERNE', libelle: 'Interne' },
];

export function libelleDuType(type: TypeCodeBarres | undefined): string {
  return TYPES_DE_CODE.find((t) => t.valeur === type)?.libelle ?? '—';
}
