-- Les transferts de marchandise d'un site a l'autre : l'entrepot qui reapprovisionne un magasin,
-- un magasin qui en depanne un autre.
--
-- En deux temps, parce que c'est ainsi que la marchandise voyage. A l'expedition, elle quitte le
-- stock du site de depart ; elle est alors « en transit », dans le camion, a personne. A la
-- reception, le site d'arrivee compte ce qui est descendu du camion et c'est cela qui entre. Ce
-- qui manque — une casse en route, un carton oublie au chargement — ne disparait pas en silence :
-- l'ecart reste sur la ligne, avec son motif.

create sequence transfert_reference_seq;

create table transfert (
    id                  bigserial primary key,
    id_entreprise       bigint not null references entreprise (id),
    reference           varchar(30) not null,
    id_site_source      bigint not null references site (id),
    id_site_destination bigint not null references site (id),
    etat                varchar(12) not null,
    commentaire         varchar(255),
    date_expedition     timestamp(6) with time zone,
    date_reception      timestamp(6) with time zone,
    creation_date       timestamp(6) with time zone not null,
    last_modified_date  timestamp(6) with time zone,
    constraint ck_transfert_etat check (etat in ('BROUILLON', 'EXPEDIE', 'RECU', 'ANNULE')),
    constraint ck_transfert_sites check (id_site_source <> id_site_destination),
    -- Expedie avant d'etre recu : une date de reception sans expedition ne decrit aucun voyage.
    constraint ck_transfert_dates check (date_reception is null or date_expedition is not null)
);

create unique index ux_transfert_reference on transfert (id_entreprise, reference);
create index ix_transfert_source on transfert (id_site_source, etat);
create index ix_transfert_destination on transfert (id_site_destination, etat);

create table ligne_transfert (
    id                 bigserial primary key,
    id_transfert       bigint not null references transfert (id) on delete cascade,
    id_article         bigint not null references article (id),
    id_conditionnement bigint references conditionnement (id),
    -- Figee a la saisie, comme sur une ligne de vente : un carton redefini depuis ne change pas
    -- ce qui est parti.
    contenance         numeric(14, 3) not null default 1,
    quantite           numeric(19, 3) not null,
    -- Nulle tant que rien n'est recu.
    quantite_recue     numeric(19, 3),
    motif_ecart        varchar(255),
    id_entreprise      bigint,
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone,
    constraint ck_ligne_transfert_quantite check (quantite > 0),
    constraint ck_ligne_transfert_recue check (quantite_recue is null or (quantite_recue >= 0 and quantite_recue <= quantite))
);

create index ix_ligne_transfert_transfert on ligne_transfert (id_transfert);

alter table mvt_stk
    drop constraint ck_mvt_stk_motif;

alter table mvt_stk
    add constraint ck_mvt_stk_motif
        check (motif is null or motif in
            ('LIVRAISON_COMMANDE', 'VENTE', 'ANNULATION_VENTE', 'CORRECTION_VENTE',
             'SAISIE_MANUELLE', 'INVENTAIRE',
             'PERTE', 'CASSE', 'PEREMPTION', 'RETOUR_FOURNISSEUR', 'RETOUR_CLIENT',
             'CONSOMMATION_INTERNE', 'TRANSFERT_SORTIE', 'TRANSFERT_ENTREE'));
