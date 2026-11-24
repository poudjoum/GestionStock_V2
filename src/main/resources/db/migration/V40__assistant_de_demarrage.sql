-- L'assistant de demarrage : les premiers pas d'un commerce qu'on vient d'inscrire.
--
-- L'avancement ne se stocke pas : il se deduit de ce que le commerce contient deja (categories,
-- articles, mouvements de stock, comptes, ventes). Seul se retient le choix du gerant de ne plus
-- le voir — sur le serveur et non sur l'appareil, pour qu'il ne reapparaisse pas sur la caisse
-- ou le telephone de la meme personne.

alter table entreprise add column demarrage_masque boolean not null default false;
