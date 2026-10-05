-- L'unite de base d'un article, ses conditionnements, et les codes-barres qui les designent.
--
-- Un grossiste vend le meme produit a la bouteille et au carton de 24, un pharmacien a la
-- plaquette et a la boite, une quincaillerie au metre et au rouleau. Le logiciel ne connaissait
-- que « une quantite » : il fallait choisir entre tenir le stock en cartons ou en bouteilles, et
-- vendre l'autre a la main.
--
-- Le stock reste tenu dans une seule unite, l'unite de base de l'article : les mouvements ne
-- changent pas, et la somme des entrees moins les sorties garde son sens. Le conditionnement ne
-- dit que deux choses : combien d'unites de base il contient, et a quel prix il se vend.

-- PIECE pour tout ce qui existe deja : c'est ce que le logiciel supposait sans le dire.
alter table article add column unite_base varchar(10) not null default 'PIECE';
alter table article add constraint ck_article_unite_base
    check (unite_base in ('PIECE', 'KG', 'LITRE', 'METRE', 'M2', 'M3'));

create table conditionnement (
    id                 bigserial primary key,
    id_article         bigint not null,
    id_entreprise      bigint,
    libelle            varchar(60) not null,
    -- Combien d'unites de base il contient : 24 bouteilles, 50 kg, 30 comprimes.
    quantite_unites    numeric(14, 3) not null,
    -- Fixe par le gerant, et non deduit du prix unitaire : le carton se vend en general moins
    -- cher que 24 bouteilles, et c'est tout l'interet de la vente en gros. Hors taxes, comme le
    -- prix de l'article ; le taux de TVA reste celui de l'article.
    prix_vente_ht      numeric(19, 2),
    vendable           boolean not null default true,
    achetable          boolean not null default true,
    -- Retire plutot que supprime : les lignes deja vendues le citent.
    actif              boolean not null default true,
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone,
    constraint fk_conditionnement_article foreign key (id_article) references article (id) on delete cascade,
    constraint ck_conditionnement_quantite check (quantite_unites > 0),
    constraint ck_conditionnement_prix check (prix_vente_ht is null or prix_vente_ht >= 0),
    -- Un conditionnement vendable a un prix : sans lui, le comptoir ne saurait pas quoi encaisser.
    constraint ck_conditionnement_prix_si_vendable check (not vendable or prix_vente_ht is not null)
);

create index ix_conditionnement_article on conditionnement (id_article);

-- Deux conditionnements actifs du meme article ne portent pas le meme nom : le caissier qui
-- choisit « Carton » ne doit pas avoir a deviner lequel des deux.
create unique index ux_conditionnement_libelle_actif
    on conditionnement (id_article, lower(libelle))
    where actif;

create table code_barres (
    id                 bigserial primary key,
    id_entreprise      bigint,
    code               varchar(64) not null,
    type_code          varchar(10) not null,
    id_article         bigint not null,
    -- Nul : le code designe l'unite de base. Le code de la bouteille et celui du carton ne sont
    -- pas les memes, et c'est ce qui permet au scan de savoir ce qu'on lui presente.
    id_conditionnement bigint,
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone,
    constraint fk_code_barres_article foreign key (id_article) references article (id) on delete cascade,
    constraint fk_code_barres_conditionnement foreign key (id_conditionnement)
        references conditionnement (id) on delete cascade,
    constraint ck_code_barres_type check (type_code in ('EAN13', 'EAN8', 'UPCA', 'ITF14', 'CODE128', 'QR', 'INTERNE'))
);

-- Un code ne designe qu'une chose dans une entreprise : la douchette ne rend que le code, et deux
-- articles qui le partageraient seraient vendus l'un pour l'autre sans que personne ne le voie.
create unique index ux_code_barres_par_entreprise
    on code_barres (id_entreprise, code)
    where id_entreprise is not null;

create index ix_code_barres_article on code_barres (id_article);

-- Les lignes gardent ce qui a ete saisi — trois cartons a tel prix — et figent la contenance du
-- conditionnement a ce moment-la. Les montants se calculent donc comme avant, quantite fois prix ;
-- seul le stock convertit, quantite fois contenance. Figee, parce qu'un carton de 24 devenu un
-- carton de 20 ne doit pas reecrire ce qui est deja sorti du magasin.
--
-- Une ligne sans conditionnement est a l'unite de base, contenance 1 : c'est le cas de toutes
-- les lignes existantes, et de toutes celles qu'enverra une caisse qui n'a pas encore ete mise a
-- jour.
alter table ligne_vente
    add column id_conditionnement bigint references conditionnement (id),
    add column contenance numeric(14, 3) not null default 1;

alter table ligne_cmnde_client
    add column id_conditionnement bigint references conditionnement (id),
    add column contenance numeric(14, 3) not null default 1;

alter table ligne_cmnde_fournisseur
    add column id_conditionnement bigint references conditionnement (id),
    add column contenance numeric(14, 3) not null default 1;

-- La facture fige le libelle, comme elle fige la designation : elle doit se relire telle quelle
-- meme si le conditionnement est renomme ou retire.
alter table ligne_facture add column conditionnement varchar(60);

-- Les motifs d'une saisie a la main : la demarque se mesure enfin, au lieu de se fondre dans
-- « saisie manuelle ».
alter table mvt_stk
    drop constraint ck_mvt_stk_motif;

alter table mvt_stk
    add constraint ck_mvt_stk_motif
        check (motif is null or motif in
            ('LIVRAISON_COMMANDE', 'VENTE', 'ANNULATION_VENTE', 'CORRECTION_VENTE',
             'SAISIE_MANUELLE', 'INVENTAIRE',
             'PERTE', 'CASSE', 'PEREMPTION', 'RETOUR_FOURNISSEUR', 'RETOUR_CLIENT',
             'CONSOMMATION_INTERNE'));
