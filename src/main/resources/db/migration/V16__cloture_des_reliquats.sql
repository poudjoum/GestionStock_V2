-- Cloture d'un reliquat.
--
-- Une commande partiellement livree dont le reste n'arrivera jamais restait bloquee dans cet
-- etat : elle figurait indefiniment parmi les commandes en cours, et rien ne permettait de la
-- solder.
--
-- Un etat distinct, et non un passage en LIVREE : un fournisseur qui a tout livre et un qui a
-- fait defaut ne doivent pas se ressembler six mois plus tard. CLOTUREE dit « on n'attend plus
-- rien », pas « tout est arrive ». Ce qui n'est jamais venu reste lisible sur les lignes.

alter table commande_fournisseur
    add column motif_cloture varchar(255);

alter table commande_client
    add column motif_cloture varchar(255);

alter table commande_fournisseur drop constraint if exists ck_commande_fournisseur_etat;
alter table commande_fournisseur
    add constraint ck_commande_fournisseur_etat
        check (etat in ('EN_PREPARATION', 'VALIDEE', 'PARTIELLEMENT_LIVREE', 'LIVREE', 'CLOTUREE', 'ANNULEE'));

alter table commande_client drop constraint if exists ck_commande_client_etat;
alter table commande_client
    add constraint ck_commande_client_etat
        check (etat in ('EN_PREPARATION', 'VALIDEE', 'PARTIELLEMENT_LIVREE', 'LIVREE', 'CLOTUREE', 'ANNULEE'));
