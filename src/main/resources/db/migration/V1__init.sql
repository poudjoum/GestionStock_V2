-- Schema initial.
--
-- Il reprend a l'identique ce qu'Hibernate generait jusqu'ici en `ddl-auto: update`, a une
-- difference pres : la table des utilisateurs s'appelle `utilisateur` et non `user`, mot reserve
-- de PostgreSQL. Les types sont ceux que le mapping impose (`spring.jpa.hibernate.ddl-auto` vaut
-- desormais `validate` et refusera de demarrer s'ils divergent) ; les cles etrangeres, elles, sont
-- nommees a la main — Hibernate leur donnait des noms haches du genre `fk11nt4550nnro1dvupx0h6bs7v`,
-- illisibles le jour ou une contrainte saute en production.

create table entreprise (
    id                 bigserial primary key,
    nom                varchar(255),
    description        varchar(255),
    registre_commerce  varchar(255),
    email              varchar(255),
    telephone          varchar(255),
    siteweb            varchar(255),
    adresse1           varchar(255),
    adresse2           varchar(255),
    ville              varchar(255),
    code_postale       varchar(255),
    pays               varchar(255),
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone
);

create table category (
    id                 bigserial primary key,
    code_cat           varchar(255),
    designation        varchar(255),
    entreprise_id      bigint,
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone
);

create table article (
    id                 bigserial primary key,
    code_article       varchar(255),
    designation        varchar(255),
    prix_unitaire      numeric(38, 2),
    tauxtva            numeric(38, 2),
    prix_unitttc       numeric(38, 2),
    photo              varchar(255),
    id_category        bigint,
    id_entreprise      bigint,
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone,
    constraint fk_article_category foreign key (id_category) references category (id)
);

create table client (
    id                 bigserial primary key,
    noms               varchar(255),
    prenoms            varchar(255),
    email              varchar(255),
    telephone          varchar(255),
    photo              varchar(255),
    adresse1           varchar(255),
    adresse2           varchar(255),
    ville              varchar(255),
    code_postale       varchar(255),
    pays               varchar(255),
    id_entreprise      bigint,
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone
);

create table fournisseur (
    id                 bigserial primary key,
    nom                varchar(255),
    prenom             varchar(255),
    mail               varchar(255),
    num_tel            varchar(255),
    photo              varchar(255),
    adresse1           varchar(255),
    adresse2           varchar(255),
    ville              varchar(255),
    code_postale       varchar(255),
    pays               varchar(255),
    id_entreprise      bigint,
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone
);

create table commande_client (
    id                 bigserial primary key,
    code               varchar(255),
    date_commande      timestamp(6) with time zone,
    id_client          bigint,
    id_entreprise      bigint,
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone,
    constraint fk_commande_client_client foreign key (id_client) references client (id)
);

create table commande_fournisseur (
    id                 bigserial primary key,
    code               varchar(255),
    date_commande      timestamp(6) with time zone,
    id_fournisseur     bigint,
    id_entreprise      bigint,
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone,
    constraint fk_commande_fournisseur_fournisseur foreign key (id_fournisseur) references fournisseur (id)
);

create table vente (
    id                 bigserial primary key,
    code               varchar(255),
    date_vente         timestamp(6) with time zone,
    commentaire        varchar(255),
    id_entreprise      bigint,
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone
);

create table ligne_cmnde_client (
    id                 bigserial primary key,
    id_article         bigint,
    id_commande_client bigint,
    quantite           numeric(38, 2),
    prix_unitaire      numeric(38, 2),
    id_entreprise      bigint,
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone,
    constraint fk_ligne_cmnde_client_article foreign key (id_article) references article (id),
    constraint fk_ligne_cmnde_client_commande foreign key (id_commande_client) references commande_client (id)
);

create table ligne_cmnde_fournisseur (
    id                      bigserial primary key,
    id_article              bigint,
    id_commande_fournisseur bigint,
    quantite                numeric(38, 2),
    prix_unitaire           numeric(38, 2),
    id_entreprise           bigint,
    creation_date           timestamp(6) with time zone not null,
    last_modified_date      timestamp(6) with time zone,
    constraint fk_ligne_cmnde_fournisseur_article foreign key (id_article) references article (id),
    constraint fk_ligne_cmnde_fournisseur_commande foreign key (id_commande_fournisseur) references commande_fournisseur (id)
);

create table ligne_vente (
    id                 bigserial primary key,
    id_article         bigint,
    id_vente           bigint,
    quantite           numeric(38, 2),
    prix_unitaire      numeric(38, 2),
    id_entreprise      bigint,
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone,
    constraint fk_ligne_vente_article foreign key (id_article) references article (id),
    constraint fk_ligne_vente_vente foreign key (id_vente) references vente (id)
);

-- Mouvements de stock. La table existe et n'est encore alimentee par personne : ni les ventes ni
-- les commandes ne l'ecrivent, et aucun service ne la lit. C'est le trou fonctionnel principal de
-- l'application.
--
-- `type_mvt` est un `smallint` parce que l'enumeration est mappee en ORDINAL : la valeur en base
-- est le rang de la constante dans TypeMvtStk, et reordonner cette enumeration reinterpreterait
-- silencieusement l'historique. `id_entreprise` y est un `integer` la ou toutes les autres tables
-- portent un `bigint` — le champ est un Integer dans l'entite. Les deux sont a reprendre avec le
-- service de mouvement de stock, pas avant : les changer ici sans toucher au mapping ferait
-- echouer la validation au demarrage.
create table mvt_stk (
    id                 bigserial primary key,
    date_mvt           timestamp(6) with time zone,
    quantite           numeric(38, 2),
    id_article         bigint,
    type_mvt           smallint,
    id_entreprise      integer,
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone,
    constraint fk_mvt_stk_article foreign key (id_article) references article (id),
    constraint ck_mvt_stk_type_mvt check (type_mvt between 0 and 1)
);

create table role (
    id                 bigserial primary key,
    role_name          varchar(255),
    id_entreprise      bigint,
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone,
    constraint ck_role_role_name check (role_name in
        ('ROLE_USER', 'ROLE_CAISSIER', 'ROLE_ADMIN', 'ROLE_COMPTABLE', 'ROLE_MANAGER', 'ROLE_MAGASINIER'))
);

create table utilisateur (
    id                 bigserial primary key,
    nom                varchar(255),
    prenoms            varchar(255),
    username           varchar(255),
    email              varchar(255),
    motdepasse         varchar(255),
    date_naissance     timestamp(6) with time zone,
    photo              varchar(255),
    num_tel            varchar(255),
    adresse1           varchar(255),
    adresse2           varchar(255),
    ville              varchar(255),
    code_postale       varchar(255),
    pays               varchar(255),
    id_entreprise      bigint,
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone,
    constraint fk_utilisateur_entreprise foreign key (id_entreprise) references entreprise (id)
);

create table user_roles (
    user_id bigint not null,
    role_id bigint not null,
    primary key (role_id, user_id),
    constraint fk_user_roles_utilisateur foreign key (user_id) references utilisateur (id),
    constraint fk_user_roles_role foreign key (role_id) references role (id)
);
