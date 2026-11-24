import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environnement } from '../../environnements/environnement';

const API = `${environnement.api}/gestiondestock/v1/entreprises/mienne/demarrage`;

export type CodeEtape =
  'IDENTITE' | 'CATEGORIES' | 'ARTICLES' | 'STOCK' | 'EQUIPE' | 'PREMIERE_VENTE';

/** Ce que le serveur sait d'une etape : faite ou non, et ce qu'elle compte deja. */
export interface EtapeDto {
  code: CodeEtape;
  faite: boolean;
  facultative: boolean;
  nombre?: number | null;
}

export interface DemarrageDto {
  etapes: EtapeDto[];
  termine: boolean;
  masque: boolean;
}

/** Ce que le front ajoute a une etape : ses mots, et l'ecran ou elle se fait. */
export interface DescriptionEtape {
  titre: string;
  texte: string;
  icone: string;
  chemin: string;
  action: string;
  /** Un second chemin, quand il y a deux facons de faire. */
  autre?: { chemin: string; action: string };
  /** Ce que compte l'etape, dit en mots : « 3 catégories ». */
  compte?: (n: number) => string;
}

const pluriel = (n: number, mot: string) => `${n} ${mot}${n > 1 ? 's' : ''}`;

export const ETAPES: Record<CodeEtape, DescriptionEtape> = {
  IDENTITE: {
    titre: 'Présenter le magasin',
    texte:
      'Adresse, logo, NIU et régime de TVA : ce que le ticket et la facture impriment sous le nom du magasin.',
    icone: 'storefront',
    chemin: '/parametres',
    action: 'Compléter le magasin',
  },
  CATEGORIES: {
    titre: 'Créer les catégories',
    texte:
      'Boissons, Quincaillerie, Hygiène… Elles rangent le catalogue et découpent les rapports.',
    icone: 'category',
    chemin: '/categories',
    action: 'Ajouter des catégories',
    compte: (n) => pluriel(n, 'catégorie'),
  },
  ARTICLES: {
    titre: 'Saisir les articles',
    texte: 'Un par un, ou tout le catalogue d’un coup depuis un classeur Excel.',
    icone: 'inventory_2',
    chemin: '/articles',
    action: 'Ajouter des articles',
    autre: { chemin: '/articles/import', action: 'Importer un classeur' },
    compte: (n) => pluriel(n, 'article'),
  },
  STOCK: {
    titre: 'Compter le stock de départ',
    texte:
      'Un inventaire d’ouverture enregistre ce qui est déjà en rayon. Sans lui, la première vente ferait passer le stock en négatif.',
    icone: 'fact_check',
    chemin: '/inventaire',
    action: 'Ouvrir un inventaire',
  },
  EQUIPE: {
    titre: 'Ajouter l’équipe',
    texte:
      'Caissiers, magasiniers, comptable : chacun son compte et son rôle. Inutile si vous tenez le magasin seul.',
    icone: 'group',
    chemin: '/comptes',
    action: 'Ajouter un collaborateur',
    compte: (n) => pluriel(n, 'collaborateur'),
  },
  PREMIERE_VENTE: {
    titre: 'Encaisser la première vente',
    texte:
      'Au comptoir : le ticket sort avec l’en-tête du magasin, et le stock se met à jour tout seul.',
    icone: 'point_of_sale',
    chemin: '/comptoir',
    action: 'Aller au comptoir',
  },
};

/** L'etape a faire maintenant : la premiere obligatoire qui ne l'est pas, sinon la premiere tout court. */
export function prochaineEtape(etat: DemarrageDto): EtapeDto | null {
  const restantes = etat.etapes.filter((e) => !e.faite);
  return restantes.find((e) => !e.facultative) ?? restantes[0] ?? null;
}

/**
 * L'assistant de demarrage d'un commerce qu'on vient d'inscrire.
 *
 * Le serveur deduit chaque etape des donnees du commerce : il n'y a rien a cocher, seulement a
 * relire apres chaque passage sur l'ecran ou elle se fait.
 */
@Injectable({ providedIn: 'root' })
export class Demarrage {
  private readonly http = inject(HttpClient);

  etat(): Observable<DemarrageDto> {
    return this.http.get<DemarrageDto>(API);
  }

  /** Ferme l'assistant sur l'accueil, ou l'y remet. Le choix se garde sur le serveur. */
  masquer(masque: boolean): Observable<DemarrageDto> {
    return this.http.put<DemarrageDto>(`${API}/masque`, null, { params: { masque } });
  }
}
