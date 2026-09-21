-- Cycle de vie des commandes.
--
-- Une commande n'avait pas d'etat : enregistree, elle etait definitive. C'est ce qui obligeait a
-- faire entrer la marchandise d'une commande fournisseur des son enregistrement, faute de moment
-- ou constater sa reception — le stock montait donc avant que le camion n'arrive.
--
-- Les commandes fournisseur deja en base ont ete saisies sous cette regle : leur marchandise est
-- deja entree en stock. Elles naissent donc LIVREE, et non EN_PREPARATION — sans quoi les passer
-- a LIVREE plus tard ferait entrer une seconde fois une marchandise deja comptee.
--
-- Les commandes client, elles, n'ont jamais touche au stock : EN_PREPARATION convient.

alter table commande_fournisseur
    add column etat varchar(20) not null default 'EN_PREPARATION';

update commande_fournisseur set etat = 'LIVREE';

alter table commande_client
    add column etat varchar(20) not null default 'EN_PREPARATION';

alter table commande_fournisseur
    add constraint ck_commande_fournisseur_etat
        check (etat in ('EN_PREPARATION', 'VALIDEE', 'LIVREE', 'ANNULEE'));

alter table commande_client
    add constraint ck_commande_client_etat
        check (etat in ('EN_PREPARATION', 'VALIDEE', 'LIVREE', 'ANNULEE'));
