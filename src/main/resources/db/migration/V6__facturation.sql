-- Facturation des ventes.
--
-- Une facture est un document fige, et non une vue calculee sur la vente. Ses lignes recopient la
-- designation, le prix et le taux de TVA tels qu'ils etaient a l'emission : une facture qui
-- relirait l'article mentirait des le premier changement de prix, et celle que le client a entre
-- les mains ne correspondrait plus a celle que le logiciel affiche.
--
-- C'est aussi pour cela que les montants sont stockes plutot que recalcules a l'affichage : ils
-- font foi.

-- Un numero de facture se suit sans trou et ne se rejoue pas. La sequence tient ce role ; elle
-- n'est pas rattachee a une colonne parce que le numero porte aussi l'annee.
create sequence facture_numero_seq start with 1 increment by 1;

create table facture (
    id                 bigserial primary key,
    numero             varchar(30) not null,
    date_emission      timestamp(6) with time zone not null,
    -- Une vente ne se facture qu'une fois : la contrainte le dit, plutot qu'un controle en Java
    -- que deux requetes simultanees contourneraient.
    id_vente           bigint not null,
    total_ht           numeric(38, 2) not null,
    total_tva          numeric(38, 2) not null,
    total_ttc          numeric(38, 2) not null,
    -- Une facture ne se supprime pas : elle s'annule, et reste lisible. Un numero emis puis
    -- disparu est precisement ce qu'une comptabilite ne doit pas montrer.
    annulee            boolean not null default false,
    id_entreprise      bigint,
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone,
    constraint ux_facture_numero unique (numero),
    constraint ux_facture_vente unique (id_vente),
    constraint fk_facture_vente foreign key (id_vente) references vente (id)
);

create table ligne_facture (
    id                 bigserial primary key,
    id_facture         bigint not null,
    code_article       varchar(255),
    designation        varchar(255),
    quantite           numeric(38, 2) not null,
    prix_unitaire_ht   numeric(38, 2) not null,
    taux_tva           numeric(38, 2) not null,
    montant_ht         numeric(38, 2) not null,
    montant_tva        numeric(38, 2) not null,
    montant_ttc        numeric(38, 2) not null,
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone,
    constraint fk_ligne_facture_facture foreign key (id_facture) references facture (id)
);

create index ix_ligne_facture_facture on ligne_facture (id_facture);
