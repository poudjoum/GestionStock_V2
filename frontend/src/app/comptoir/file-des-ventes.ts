import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environnement } from '../../environnements/environnement';
import { effacer, ecrire, toutLire } from '../noyau/base-locale';
import { messageDErreur } from '../noyau/erreurs';
import { Reseau } from '../noyau/reseau';
import { Session } from '../noyau/session';
import type { ReglementDto } from '../noyau/reglements';
import type { VenteDto } from './comptoir.service';

const API = `${environnement.api}/gestiondestock/v1`;

/** Tous les combien on retente, tant qu'il reste des ventes a envoyer. */
const RELANCE_MS = 30_000;

/**
 * La vente telle qu'elle part au serveur : la vente, et ce que le client a paye.
 *
 * `encaissement` voyage avec elle — voir `VenteDto.encaissement` cote serveur. Envoye a part, le
 * reglement serait date du jour de l'envoi et rangerait les especes de lundi dans la caisse de
 * mardi ; rejoue apres une reponse perdue, il serait encaisse deux fois.
 */
export type VenteSynchronisee = VenteDto & { encaissement: ReglementDto };

/** Une vente faite sur cet appareil et pas encore recue par le serveur. */
export interface VenteEnAttente {
  referenceClient: string;
  /** Le compte qui a vendu : c'est lui, et lui seul, qui peut l'envoyer. */
  username: string;
  vente: VenteSynchronisee;
  /** Le total annonce au client, pour l'afficher sans recalculer. */
  totalTtc: number;
  /**
   * Refusee par le serveur, et non simplement pas encore partie.
   *
   * Un refus ne se reglera pas en reessayant toutes les trente secondes — un article supprime
   * entre-temps, une horloge d'appareil tres en avance. La vente reste la, avec le motif, et
   * attend qu'une personne s'en occupe. Elle n'est jamais effacee d'office : l'argent est dans le
   * tiroir.
   */
  refusee: boolean;
  motif?: string;
}

/**
 * Les ventes faites sans reseau, gardees sur l'appareil jusqu'a ce que le serveur les ait recues.
 *
 * <b>Rien ne se perd.</b> La vente est ecrite dans la base de l'appareil avant que le ticket ne
 * sorte ; elle n'en est effacee qu'une fois la reponse du serveur recue. Entre les deux, fermer
 * l'onglet, eteindre le telephone ou perdre la batterie ne change rien : elle repartira.
 *
 * <b>Rien ne se vend deux fois.</b> Chaque vente porte la reference que le poste a tiree, et le
 * serveur rend la vente deja recue au lieu d'en creer une seconde. Renvoyer une vente dont la
 * reponse s'est perdue est donc sans danger — c'est meme le seul moyen de savoir.
 *
 * <b>Qui envoie.</b> Chaque vente porte le compte qui l'a faite. Seul ce compte l'envoie : l'API
 * range une vente dans le commerce du compte connecte, et un autre compte sur le meme appareil la
 * ferait entrer chez lui. Les ventes d'un compte deconnecte attendent sa prochaine connexion.
 */
@Injectable({ providedIn: 'root' })
export class FileDesVentes {
  private readonly http = inject(HttpClient);
  private readonly session = inject(Session);
  private readonly reseau = inject(Reseau);

  private readonly toutes = signal<VenteEnAttente[]>([]);
  private readonly envoi = signal(false);
  private envoiEnCours: Promise<void> | null = null;
  private relance?: ReturnType<typeof setInterval>;

  /** Les ventes de ce compte, les plus anciennes d'abord. */
  readonly miennes = computed(() =>
    this.toutes()
      .filter((v) => v.username === this.session.username())
      .sort((a, b) => (a.vente.datevente ?? '').localeCompare(b.vente.datevente ?? '')),
  );
  readonly aEnvoyer = computed(() => this.miennes().filter((v) => !v.refusee));
  readonly refusees = computed(() => this.miennes().filter((v) => v.refusee));
  /** Vrai pendant un envoi. */
  readonly enCours = this.envoi.asReadonly();

  constructor() {
    void this.relire();

    // Le retour du serveur, ou l'arrivee d'un compte, sont les deux moments ou une vente en
    // attente peut enfin partir.
    effect(() => {
      if (this.reseau.joignable() && this.session.connecte()) {
        // `untracked` : l'envoi lit la file et ecrit son etat. Sans cela, ces lectures
        // deviendraient des dependances de l'effet, qui se relancerait a chaque vente envoyee.
        untracked(() => void this.envoyer());
      }
    });

    // Et une relance reguliere, pour ce qui a echoue sans que le reseau ne change d'etat : un
    // serveur en cours de redemarrage repond 502 sans jamais passer hors ligne.
    effect(() => {
      if (this.aEnvoyer().length > 0) {
        this.relance ??= setInterval(() => void this.envoyer(), RELANCE_MS);
      } else {
        clearInterval(this.relance);
        this.relance = undefined;
      }
    });
  }

  /**
   * Garde une vente sur l'appareil.
   *
   * La promesse n'aboutit qu'une fois la vente ecrite sur le disque : c'est seulement alors que le
   * comptoir peut sortir le ticket. Si l'ecriture echoue, la promesse echoue, et le caissier doit
   * le savoir avant de rendre la monnaie.
   */
  async garder(vente: VenteSynchronisee, totalTtc: number): Promise<void> {
    const enAttente: VenteEnAttente = {
      referenceClient: vente.referenceClient!,
      username: this.session.username() ?? '',
      vente,
      totalTtc,
      refusee: false,
    };
    await ecrire('ventes', enAttente);
    this.toutes.update((liste) => [
      ...liste.filter((v) => v.referenceClient !== enAttente.referenceClient),
      enAttente,
    ]);
    void this.envoyer();
  }

  /** Remet une vente refusee dans la file, apres qu'on a regle ce qui la bloquait. */
  async reessayer(referenceClient: string): Promise<void> {
    const vente = this.toutes().find((v) => v.referenceClient === referenceClient);
    if (!vente) {
      return;
    }
    await this.remplacer({ ...vente, refusee: false, motif: undefined });
    await this.envoyer();
  }

  /**
   * Envoie ce qui attend. Un seul envoi a la fois.
   *
   * Un seul, parce que deux envois simultanes de la meme vente feraient chacun leur facture avant
   * que l'un ne voie celle de l'autre.
   */
  envoyer(): Promise<void> {
    this.envoiEnCours ??= this.toutEnvoyer().finally(() => {
      this.envoiEnCours = null;
      this.envoi.set(false);
    });
    return this.envoiEnCours;
  }

  private async toutEnvoyer(): Promise<void> {
    if (!this.session.connecte()) {
      return;
    }
    this.envoi.set(true);
    for (const enAttente of this.aEnvoyer()) {
      try {
        await firstValueFrom(
          this.http.post<VenteDto>(`${API}/ventes/synchronisation`, enAttente.vente),
        );
        await effacer('ventes', enAttente.referenceClient);
        this.toutes.update((liste) =>
          liste.filter((v) => v.referenceClient !== enAttente.referenceClient),
        );
      } catch (echec: unknown) {
        if (!(echec instanceof HttpErrorResponse)) {
          // L'effacement local a echoue apres un envoi reussi : la vente repartira, et le
          // serveur la reconnaitra a sa reference. Rien de plus a faire.
          return;
        }
        if (echec.status === 0 || echec.status === 401 || echec.status >= 500) {
          // Pas de reseau, plus de session, ou un serveur qui ne va pas bien : rien de ce qui
          // suit ne passerait mieux. On s'arrete, et on retentera.
          return;
        }
        // Le serveur a lu la vente et la refuse. Reessayer ne changera rien : elle attend une
        // personne. Les suivantes, elles, partent.
        await this.remplacer({
          ...enAttente,
          refusee: true,
          motif: messageDErreur(echec, 'Le serveur a refusé cette vente.'),
        });
      }
    }
  }

  private async relire(): Promise<void> {
    try {
      this.toutes.set(await toutLire<VenteEnAttente>('ventes'));
    } catch {
      this.toutes.set([]);
      return;
    }
    // Au demarrage, l'effet qui declenche l'envoi passe avant que la relecture du disque ne
    // soit finie : il trouve une file vide et ne fait rien. Sans cet appel, les ventes de la
    // veille attendaient la relance suivante, trente secondes plus tard.
    void this.envoyer();
  }

  private async remplacer(vente: VenteEnAttente): Promise<void> {
    await ecrire('ventes', vente);
    this.toutes.update((liste) =>
      liste.map((v) => (v.referenceClient === vente.referenceClient ? vente : v)),
    );
  }
}
