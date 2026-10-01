-- Programme de fidelite pour les clients de l'application mobile et bons d'achat.
--
-- Permet aux clients de scanner les tickets de caisse de commerces abonnes et d'accumuler des points
-- par magasin, puis de les convertir en bons d'achat a valoir lors de leurs prochains passages.

-- 1. Compte client mobile (authentification simplifiee par telephone et mot de passe/code secret).
create table compte_client_fidelite (
    id                 bigserial primary key,
    telephone          varchar(30) not null,
    nom                varchar(100),
    prenom             varchar(100),
    mot_de_passe       varchar(255) not null,
    actif              boolean not null default true,
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone,
    constraint uq_client_fidelite_telephone unique (telephone)
);

-- 2. Solde des points de fidelite par magasin (points gagnes et solde courant).
create table solde_points_magasin (
    id                   bigserial primary key,
    id_client_fidelite   bigint not null,
    id_entreprise        bigint not null,
    solde_points         integer not null default 0,
    points_cumules_total integer not null default 0,
    creation_date        timestamp(6) with time zone not null,
    last_modified_date   timestamp(6) with time zone,
    constraint uq_solde_client_entreprise unique (id_client_fidelite, id_entreprise),
    constraint fk_solde_client foreign key (id_client_fidelite) references compte_client_fidelite (id),
    constraint fk_solde_entreprise foreign key (id_entreprise) references entreprise (id),
    constraint ck_solde_points_positif check (solde_points >= 0)
);

create index ix_solde_client on solde_points_magasin (id_client_fidelite);
create index ix_solde_entreprise on solde_points_magasin (id_entreprise);

-- 3. Tickets reclames : empeche le double scan (un code de ticket unique ne se reclame qu'une seule fois).
create table ticket_reclame (
    id                   bigserial primary key,
    code_ticket          varchar(12) not null,
    id_client_fidelite   bigint not null,
    id_entreprise        bigint not null,
    id_vente             bigint not null,
    points_attribues     integer not null,
    montant_achat_ttc    numeric(38, 2) not null,
    date_reclamation     timestamp(6) with time zone not null,
    creation_date        timestamp(6) with time zone not null,
    last_modified_date   timestamp(6) with time zone,
    constraint uq_ticket_reclame_code unique (code_ticket),
    constraint fk_ticket_reclame_client foreign key (id_client_fidelite) references compte_client_fidelite (id),
    constraint fk_ticket_reclame_entreprise foreign key (id_entreprise) references entreprise (id),
    constraint fk_ticket_reclame_vente foreign key (id_vente) references vente (id)
);

create index ix_ticket_reclame_client on ticket_reclame (id_client_fidelite);
create index ix_ticket_reclame_entreprise on ticket_reclame (id_entreprise);

-- 4. Bons d'achat emis en echange des points de fidelite.
create table bon_achat (
    id                   bigserial primary key,
    code_bon             varchar(32) not null,
    id_client_fidelite   bigint not null,
    id_entreprise        bigint not null,
    points_utilises      integer not null,
    montant_fcfa         numeric(38, 2) not null,
    statut               varchar(20) not null default 'ACTIF',
    date_emission        timestamp(6) with time zone not null,
    date_expiration      timestamp(6) with time zone not null,
    date_utilisation     timestamp(6) with time zone,
    id_vente_utilisation bigint,
    creation_date        timestamp(6) with time zone not null,
    last_modified_date   timestamp(6) with time zone,
    constraint uq_bon_achat_code unique (code_bon),
    constraint ck_bon_achat_statut check (statut in ('ACTIF', 'UTILISE', 'EXPIRE')),
    constraint fk_bon_achat_client foreign key (id_client_fidelite) references compte_client_fidelite (id),
    constraint fk_bon_achat_entreprise foreign key (id_entreprise) references entreprise (id),
    constraint fk_bon_achat_vente foreign key (id_vente_utilisation) references vente (id)
);

create index ix_bon_achat_client on bon_achat (id_client_fidelite);
create index ix_bon_achat_entreprise on bon_achat (id_entreprise);
create index ix_bon_achat_code on bon_achat (code_bon);
