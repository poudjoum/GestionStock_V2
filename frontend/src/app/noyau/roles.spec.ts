import { describe, expect, it } from 'vitest';
import { accueilPour, groupesPour } from './roles';

describe('groupesPour', () => {
  const noms = (roles: Parameters<typeof groupesPour>[0]) =>
    groupesPour(roles).map((g) => [g.libelle, g.entrees.map((e) => e.libelle)]);

  it('ne montre au caissier que ses groupes, sans groupe vide', () => {
    expect(noms(['ROLE_CAISSIER'])).toEqual([
      ['Ventes', ['Vendre', 'Caisse']],
      ['Clients et promos', ['Clients']],
    ]);
  });

  it('range les fournisseurs avec les achats pour le magasinier', () => {
    expect(noms(['ROLE_MAGASINIER'])).toEqual([
      ['Stock', ['Stock', 'Catalogue', 'Inventaire', 'Transferts']],
      ['Achats', ['Commandes', 'Fournisseurs']],
    ]);
  });

  it('met l’accueil en tête, hors groupe, pour le gérant', () => {
    const groupes = groupesPour(['ROLE_MANAGER']);
    expect(groupes[0]).toMatchObject({ id: null, libelle: null });
    expect(groupes[0].entrees.map((e) => e.chemin)).toEqual(['/accueil']);
    expect(groupes.map((g) => g.libelle)).toEqual([
      null,
      'Ventes',
      'Stock',
      'Achats',
      'Clients et promos',
      // L'equipe : le gerant y ajoute ses caissiers et ses magasiniers.
      'Réglages',
    ]);
    expect(groupes.at(-1)?.entrees.map((e) => e.chemin)).toEqual(['/comptes', '/sites']);
  });
});

describe('accueilPour', () => {
  it('garde à chaque rôle son écran d’arrivée', () => {
    expect(accueilPour(['ROLE_SUPER_ADMIN'])).toBe('/commerces');
    expect(accueilPour(['ROLE_ADMIN'])).toBe('/accueil');
    expect(accueilPour(['ROLE_CAISSIER'])).toBe('/comptoir');
    expect(accueilPour(['ROLE_MAGASINIER'])).toBe('/stock');
    expect(accueilPour(['ROLE_COMPTABLE'])).toBe('/accueil');
  });
});
