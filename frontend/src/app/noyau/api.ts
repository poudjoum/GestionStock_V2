import type { components } from '../api/schema';

/**
 * Les formes que l'API rend, telles qu'elle les decrit.
 *
 * Elles ne sont pas recopiees a la main : `npm run api:types` les regenere depuis la
 * specification OpenAPI du serveur. Recopier des DTO est le plus sur moyen de les laisser
 * diverger — le jour ou le backend ajoute un champ, personne ne s'en apercoit ici.
 */
type Schemas = components['schemas'];

export type JwtResponse = Schemas['JwtResponse'];
export type UserDto = Schemas['UserDto'];
export type RoleDto = Schemas['RoleDto'];
export type ArticleDto = Schemas['ArticleDto'];
export type ClientDto = Schemas['ClientDto'];
export type NotificationDto = Schemas['NotificationDto'];
export type LigneInventaireDto = Schemas['LigneInventaireDto'];

/** L'identite du magasin, telle qu'elle s'imprime en tete d'un ticket. */
export type EntrepriseDto = Schemas['EntrepriseDto'];

/**
 * Une page telle que Spring Data la rend.
 *
 * Le type genere depuis la specification perd le contenu — springdoc decrit `Page` sans son
 * parametre — d'ou cette forme, qui est la seule ecrite a la main de ce fichier.
 */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

/** Le corps d'erreur que rend le gestionnaire d'exceptions de l'API. */
export interface ErreurApi {
  httpCode?: number;
  errorCode?: string;
  message?: string;
  errors?: string[];
}
