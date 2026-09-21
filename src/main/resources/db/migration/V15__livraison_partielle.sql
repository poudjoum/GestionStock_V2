-- Livraison partielle des commandes.
--
-- Une commande se soldait d'un coup : recevoir 6 unites sur 10 obligeait soit a mentir en
-- declarant tout livre, soit a ne rien enregistrer du tout en attendant le reste. Les deux
-- faussent le stock, et le second le fausse plus longtemps.
--
-- La quantite livree se porte sur la ligne : c'est la seule facon de savoir ce qui reste attendu,
-- article par article. Un compteur global sur la commande ne dirait pas quel article manque.

alter table ligne_cmnde_fournisseur
    add column quantite_livree numeric(38, 2) not null default 0;

alter table ligne_cmnde_client
    add column quantite_livree numeric(38, 2) not null default 0;

-- Les lignes des commandes deja livrees le sont entierement : sans cette reprise, elles
-- paraitraient attendre encore toute leur marchandise, et l'on pourrait la recevoir une
-- seconde fois.
update ligne_cmnde_fournisseur l
set quantite_livree = l.quantite
from commande_fournisseur c
where c.id = l.id_commande_fournisseur
  and c.etat = 'LIVREE';

update ligne_cmnde_client l
set quantite_livree = l.quantite
from commande_client c
where c.id = l.id_commande_client
  and c.etat = 'LIVREE';

-- Une commande client peut desormais etre servie par plusieurs ventes : un acompte de
-- marchandise aujourd'hui, le reste a l'arrivee du reassort. L'index unique pose par la V7
-- l'interdisait — il portait la regle « une commande ne se sert qu'une fois », vraie tant que
-- la livraison etait indivisible.
--
-- Ce qu'il protegeait — sortir deux fois la meme marchandise — est desormais garanti par le
-- reliquat : on ne sert jamais au-dela de ce qui reste du, ligne par ligne. L'index redevient
-- donc un simple index de recherche.
drop index if exists ux_vente_commande_client;
create index ix_vente_commande_client on vente (id_commande_client);

-- Le nouvel etat intermediaire.
alter table commande_fournisseur drop constraint if exists ck_commande_fournisseur_etat;
alter table commande_fournisseur
    add constraint ck_commande_fournisseur_etat
        check (etat in ('EN_PREPARATION', 'VALIDEE', 'PARTIELLEMENT_LIVREE', 'LIVREE', 'ANNULEE'));

alter table commande_client drop constraint if exists ck_commande_client_etat;
alter table commande_client
    add constraint ck_commande_client_etat
        check (etat in ('EN_PREPARATION', 'VALIDEE', 'PARTIELLEMENT_LIVREE', 'LIVREE', 'ANNULEE'));
