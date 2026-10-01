-- Les campagnes de promotion des magasins, et les articles qu'elles mettent en promotion.
--
-- Une campagne est une periode : du premier au dernier jour, compris, a l'heure du magasin.
-- Pendant ce temps, ses articles se vendent au prix reduit — en caisse, et non seulement dans
-- l'application — et les tickets rapportent des points, a scanner avant la fin de la campagne.
--
-- La reduction se choisit article par article : un pourcentage du prix, ou un prix fixe. Le prix
-- fixe est hors taxes, comme le prix de l'article et celui de la ligne de vente.
create table campagne (
    id                 bigserial primary key,
    id_entreprise      bigint not null,
    titre              varchar(120) not null,
    message            varchar(1000),
    image              text,
    date_debut         date not null,
    date_fin           date not null,
    -- Arretee avant son terme. La campagne reste en base : ses ventes l'ont citee.
    arretee            boolean not null default false,
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone,
    constraint fk_campagne_entreprise foreign key (id_entreprise) references entreprise (id),
    constraint ck_campagne_dates check (date_fin >= date_debut)
);

create index ix_campagne_entreprise_dates on campagne (id_entreprise, date_debut, date_fin);

create table promotion_article (
    id                 bigserial primary key,
    id_campagne        bigint not null,
    id_article         bigint not null,
    id_entreprise      bigint not null,
    type_remise        varchar(20) not null,
    valeur             numeric(12, 2) not null,
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone,
    constraint fk_promotion_campagne foreign key (id_campagne) references campagne (id) on delete cascade,
    constraint fk_promotion_article foreign key (id_article) references article (id),
    constraint uq_promotion_campagne_article unique (id_campagne, id_article),
    constraint ck_promotion_type check (type_remise in ('POURCENTAGE', 'PRIX_FIXE')),
    constraint ck_promotion_valeur check (valeur > 0),
    constraint ck_promotion_pourcentage check (type_remise <> 'POURCENTAGE' or valeur < 100)
);

create index ix_promotion_article on promotion_article (id_article);
