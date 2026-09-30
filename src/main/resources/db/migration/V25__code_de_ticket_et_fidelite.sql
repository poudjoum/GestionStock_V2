-- Le code du ticket, et le programme de fidelite de chaque commerce.
--
-- Chaque ticket porte un QR qui menera le client a son achat : le magasin, la date, les articles,
-- et les points que ce ticket lui vaut. Le jour du jeu, c'est ce code qui se reclamera.

-- Douze caracteres de l'alphabet de Crockford, soit soixante bits tires au hasard : impossible a
-- deviner, et assez court pour se recopier a la main si le QR est abime. Le poste de vente le tire
-- lui-meme, pour qu'un ticket imprime hors ligne porte deja le sien.
--
-- Nul pour les ventes d'avant : leurs tickets sont imprimes, sans QR, et le resteront.
alter table vente add column code_ticket varchar(12);
alter table vente add constraint uq_vente_code_ticket unique (code_ticket);

-- Un point par tranche entiere de `montant_par_point` payee TTC sur un ticket. Reglable par
-- commerce : dix mille francs ne veulent pas dire la meme chose pour une quincaillerie et pour une
-- boutique a cinq cents francs l'article.
alter table entreprise add column fidelite_active boolean not null default true;
alter table entreprise add column montant_par_point numeric(12, 2) not null default 10000;
alter table entreprise add constraint ck_entreprise_montant_par_point check (montant_par_point > 0);
