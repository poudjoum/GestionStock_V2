-- Les sites d'une entreprise : ses magasins et ses entrepots.
--
-- Le stock etait celui de l'entreprise, en un seul tas : un commerce qui ouvre un second magasin,
-- ou qui garde sa reserve dans un depot, ne pouvait pas dire ou etait sa marchandise. Le stock se
-- tient desormais par site, et c'est toujours la somme des mouvements — de ce site.
--
-- Un magasin vend ; un entrepot ne vend pas, il livre les magasins et, sur commande, leurs
-- clients. Les prix et la fidelite restent ceux de l'entreprise.

create table site (
    id                 bigserial primary key,
    id_entreprise      bigint not null,
    nom                varchar(80) not null,
    type_site          varchar(10) not null,
    adresse            varchar(255),
    telephone          varchar(30),
    -- Le site de qui n'en a pas choisi : celui ou tombent les mouvements d'un compte sans site
    -- attribue, et ceux de toutes les donnees anterieures aux sites.
    principal          boolean not null default false,
    -- Ferme plutot que supprime : son historique de mouvements reste lisible.
    actif              boolean not null default true,
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone,
    constraint fk_site_entreprise foreign key (id_entreprise) references entreprise (id),
    constraint ck_site_type check (type_site in ('MAGASIN', 'ENTREPOT'))
);

create unique index ux_site_nom_actif on site (id_entreprise, lower(nom)) where actif;
create unique index ux_site_principal on site (id_entreprise) where principal;

-- Chaque entreprise existante recoit son magasin : c'est la qu'etait son stock.
insert into site (id_entreprise, nom, type_site, principal, creation_date)
select id, 'Magasin principal', 'MAGASIN', true, now() from entreprise;

-- Les mouvements portent leur site. Nul seulement pour les donnees anterieures au cloisonnement,
-- qui n'ont pas d'entreprise et donc pas de site.
alter table mvt_stk add column id_site bigint references site (id);
update mvt_stk m set id_site = s.id from site s where s.id_entreprise = m.id_entreprise and s.principal;
create index ix_mvt_stk_article_site on mvt_stk (id_article, id_site);

-- La vente a lieu dans un magasin — sa caisse —, et sa marchandise part d'un site : le meme au
-- comptoir, l'entrepot quand il livre le client d'une commande.
alter table vente add column id_site bigint references site (id);
alter table vente add column id_site_expedition bigint references site (id);
update vente v set id_site = s.id, id_site_expedition = s.id
from site s where s.id_entreprise = v.id_entreprise and s.principal;

-- La commande client est prise par un magasin ; la commande fournisseur est livree a un site.
alter table commande_client add column id_site bigint references site (id);
-- Le site qui la livrera : le magasin lui-meme, ou l'entrepot quand le magasin n'a pas de quoi.
-- Choisi a la prise de commande, il dit aussi a l'equipe de l'entrepot ce qu'elle a a preparer.
alter table commande_client add column id_site_expedition bigint references site (id);
update commande_client c set id_site = s.id, id_site_expedition = s.id
from site s where s.id_entreprise = c.id_entreprise and s.principal;

alter table commande_fournisseur add column id_site bigint references site (id);
update commande_fournisseur c set id_site = s.id from site s where s.id_entreprise = c.id_entreprise and s.principal;

-- L'inventaire compte un site. Une seance ouverte a la fois par site, et non plus par entreprise :
-- l'entrepot se compte pendant que le magasin vend.
alter table seance_inventaire add column id_site bigint references site (id);
update seance_inventaire i set id_site = s.id from site s where s.id_entreprise = i.id_entreprise and s.principal;
drop index ux_seance_inventaire_ouverte;
create unique index ux_seance_inventaire_ouverte
    on seance_inventaire (id_site)
    where statut = 'OUVERTE';

-- Les sites d'un collaborateur. Aucun : il travaille au site principal. L'administrateur et le
-- gerant, eux, voient tous les sites sans en avoir d'attribue.
create table utilisateur_site (
    id_utilisateur bigint not null references utilisateur (id) on delete cascade,
    id_site        bigint not null references site (id),
    primary key (id_utilisateur, id_site)
);

-- Le site sur lequel il arrive en se connectant.
alter table utilisateur add column id_site_defaut bigint references site (id);

-- Le seuil d'alerte d'un article dans un site. Sans ligne ici, le seuil de l'article s'applique :
-- le magasin veut deux cartons en rayon, l'entrepot cinquante.
create table article_site (
    id                 bigserial primary key,
    id_article         bigint not null references article (id) on delete cascade,
    id_site            bigint not null references site (id),
    id_entreprise      bigint,
    seuil_alerte       numeric(19, 3),
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone,
    constraint uq_article_site unique (id_article, id_site),
    constraint ck_article_site_seuil check (seuil_alerte is null or seuil_alerte >= 0)
);
