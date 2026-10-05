import type { StatutAffiche } from '../noyau/statuts';
import type { EtatCommande } from './commandes.service';

/** L'etat d'une commande client, dit partout de la meme facon. */
const STATUTS: Record<EtatCommande, StatutAffiche> = {
  EN_PREPARATION: { libelle: 'Brouillon', ton: 'neutre', icone: 'edit' },
  VALIDEE: { libelle: 'À servir', ton: 'ok', icone: 'inventory' },
  PARTIELLEMENT_LIVREE: { libelle: 'Reliquat', ton: 'alerte', icone: 'hourglass_bottom' },
  LIVREE: { libelle: 'Servie', ton: 'ok', icone: 'check' },
  CLOTUREE: { libelle: 'Close', ton: 'neutre', icone: 'do_not_disturb_on' },
  ANNULEE: { libelle: 'Annulée', ton: 'neutre', icone: 'block' },
};

export function statutDeCommandeClient(commande: { etat?: EtatCommande }): StatutAffiche {
  return STATUTS[commande.etat ?? 'EN_PREPARATION'];
}
