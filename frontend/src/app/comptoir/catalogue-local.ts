import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import { lire, ecrire, toutLire, toutRemplacer } from '../noyau/base-locale';
import { Session } from '../noyau/session';
import type { ArticleDto } from '../noyau/api';
import {
  jourLocal,
  prixPromotionnel,
  promotionEnCours,
  PromotionArticleDto,
  PromotionsDuJourDto,
} from './prix-promotionnel';

const API = `${environnement.api}/gestiondestock/v1`;

/** Ce qu'on retient du catalogue garde : a qui il appartient, et de quand il date. */
interface Etiquette {
  proprietaire: string;
  date: string;
}

/**
 * Une copie du catalogue sur l'appareil, pour vendre sans reseau.
 *
 * Hors ligne, le comptoir doit encore trouver un article par son code-barres, et en connaitre le
 * prix et le taux de TVA. Sans cette copie, la premiere coupure arreterait la caisse au premier
 * scan.
 *
 * Elle est rafraichie a chaque ouverture du comptoir en ligne, puis toutes les dix minutes. Un prix
 * change pendant la coupure n'y sera donc pas : c'est le prix connu au dernier rafraichissement
 * qui est vendu, et qui part au serveur avec la vente — c'est bien celui que le client a paye.
 *
 * <b>A qui elle appartient.</b> Le catalogue est celui du commerce du compte connecte. Un autre
 * compte qui se connecte sur le meme appareil — un autre commerce — ne doit pas vendre avec : la
 * copie porte le nom de son proprietaire, et une copie etrangere est tenue pour absente.
 */
@Injectable({ providedIn: 'root' })
export class CatalogueLocal {
  private readonly http = inject(HttpClient);
  private readonly session = inject(Session);

  private readonly articles = signal<ArticleDto[]>([]);
  /**
   * Les promotions du jour, gardees avec le catalogue : hors ligne, la caisse vend encore au prix
   * de la campagne. Chacune porte ses dates, et n'est appliquee que le jour ou elle vaut — une
   * caisse restee sans reseau ne la prolonge pas.
   */
  private readonly duJour = signal<PromotionsDuJourDto>({ campagnes: [], promotions: [] });
  private readonly promotions = computed(() => this.duJour().promotions);
  private readonly promotionsParArticle = computed(() => {
    const index = new Map<number, PromotionArticleDto>();
    for (const promotion of this.promotions()) {
      index.set(promotion.idArticle, promotion);
    }
    return index;
  });
  private readonly etiquette = signal<Etiquette | null>(null);
  private rafraichissementEnCours: Promise<void> | null = null;

  /**
   * Les articles gardes, pour la grille du comptoir : on vend au toucher sans attendre le
   * serveur, en ligne comme hors ligne. Lecture seule — seule la copie les ecrit.
   */
  readonly tous = this.articles.asReadonly();

  /** Le nombre d'articles gardes. Zero : l'appareil ne peut rien vendre hors ligne. */
  readonly taille = computed(() => this.articles().length);
  /** Quand la copie a ete faite. */
  readonly date = computed(() => this.etiquette()?.date ?? null);

  private readonly parCodes = computed(() => {
    const index = new Map<string, ArticleDto>();
    for (const article of this.articles()) {
      if (article.codeArticle) {
        index.set(article.codeArticle, article);
      }
    }
    return index;
  });

  private chargement: Promise<void> | null = null;
  private chargePour: string | null = null;

  /**
   * Relit la copie gardee sur l'appareil, si elle est a ce compte. Une fois par compte.
   *
   * Le rafraichissement l'attend avant d'ecrire : sans cela, une relecture lente du disque
   * pouvait arriver apres la copie fraiche du serveur, et la remplacer par l'ancienne.
   */
  charger(): Promise<void> {
    const compte = this.session.username();
    if (!this.chargement || this.chargePour !== compte) {
      this.chargePour = compte;
      this.chargement = this.relire();
    }
    return this.chargement;
  }

  private async relire(): Promise<void> {
    try {
      const etiquette = await lire<Etiquette>('reglages', 'catalogue');
      if (!etiquette || etiquette.proprietaire !== this.session.username()) {
        this.articles.set([]);
        this.etiquette.set(null);
        return;
      }
      this.articles.set(await toutLire<ArticleDto>('catalogue'));
      this.duJour.set(
        (await lire<PromotionsDuJourDto>('reglages', 'promotions')) ?? { campagnes: [], promotions: [] },
      );
      this.etiquette.set(etiquette);
    } catch {
      // Pas de base sur cet appareil : on vend en ligne comme avant, sans copie.
      this.articles.set([]);
    }
  }

  /** Recopie le catalogue depuis le serveur. Un seul a la fois. */
  rafraichir(): Promise<void> {
    this.rafraichissementEnCours ??= this.recopier().finally(() => {
      this.rafraichissementEnCours = null;
    });
    return this.rafraichissementEnCours;
  }

  /** Rafraichit si la copie a plus de dix minutes, ou n'existe pas. */
  rafraichirSiAncien(): Promise<void> {
    const date = this.date();
    if (date && Date.now() - new Date(date).getTime() < 10 * 60_000) {
      return Promise.resolve();
    }
    return this.rafraichir();
  }

  private async recopier(): Promise<void> {
    const proprietaire = this.session.username();
    if (!proprietaire) {
      return;
    }
    await this.charger();
    const [articles, promotions] = await Promise.all([
      firstValueFrom(this.http.get<ArticleDto[]>(`${API}/articles/all`)),
      // Sans les promotions, on vend quand meme : le serveur applique de lui-meme le prix de la
      // campagne a toute vente faite en ligne.
      firstValueFrom(
        this.http.get<PromotionsDuJourDto>(`${API}/campagnes/promotions-en-cours`),
      ).catch(() => this.duJour()),
    ]);
    const etiquette: Etiquette = { proprietaire, date: new Date().toISOString() };
    try {
      await toutRemplacer('catalogue', articles);
      await ecrire('reglages', promotions, 'promotions');
      await ecrire('reglages', etiquette, 'catalogue');
    } catch {
      // L'appareil ne garde rien, mais la copie en memoire sert encore tant que la page vit.
    }
    this.articles.set(articles);
    this.duJour.set(promotions);
    this.etiquette.set(etiquette);
  }

  /**
   * Si une campagne du magasin vaut aujourd'hui : ses tickets rapportent des points, et le ticket
   * imprime le dit. Hors campagne, il n'en annonce pas — le client serait refuse en le scannant.
   */
  enCampagne(): boolean {
    const jour = jourLocal();
    return this.duJour().campagnes.some((c) => promotionEnCours(c, jour));
  }

  /** La promotion qui vaut aujourd'hui pour cet article, s'il en a une. */
  promotionDe(article: ArticleDto): PromotionArticleDto | null {
    const promotion = article.id == null ? undefined : this.promotionsParArticle().get(article.id);
    return promotion && promotionEnCours(promotion, jourLocal()) ? promotion : null;
  }

  /** Le prix hors taxes auquel l'article se vend aujourd'hui : celui de sa promotion, s'il en a une. */
  prixDe(article: ArticleDto): number {
    const normal = article.prixUnitaireHt ?? 0;
    const promotion = this.promotionDe(article);
    return promotion ? prixPromotionnel(normal, promotion.typeRemise, promotion.valeur) : normal;
  }

  /** L'article qui porte exactement ce code : ce que rend une douchette. */
  parCode(code: string): ArticleDto | null {
    return this.parCodes().get(code.trim()) ?? null;
  }

  /**
   * Les articles dont la designation ou le code contient ce qui est tape.
   *
   * Sans accents ni majuscules : au comptoir, on tape « creme » pour « Crème », et un caissier
   * ne doit pas avoir a se souvenir de la graphie exacte du catalogue.
   */
  chercher(q: string, taille = 20): ArticleDto[] {
    const cherche = normaliser(q);
    if (!cherche) {
      return [];
    }
    return this.articles()
      .filter(
        (a) =>
          normaliser(a.designation ?? '').includes(cherche) ||
          normaliser(a.codeArticle ?? '').includes(cherche),
      )
      .sort((a, b) => (a.designation ?? '').localeCompare(b.designation ?? '', 'fr'))
      .slice(0, taille);
  }
}

function normaliser(texte: string): string {
  return texte
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .trim();
}
