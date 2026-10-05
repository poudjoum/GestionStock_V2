-- Les lots et leurs dates : pour l'alimentaire et la pharmacie, savoir ce qui perime, vendre
-- d'abord ce qui perime le premier, et retrouver a qui on a vendu un lot rappele.
--
-- Le suivi est une case de l'article : seuls les articles coches demandent un numero de lot et une
-- date a la reception. Le ciment et le Coca-Cola restent comme avant.
--
-- Le stock d'un lot, comme celui d'un article ou d'un site, est la somme de ses mouvements.

alter table article add column suivi_lot boolean not null default false;
-- DLC : « a consommer jusqu'au » — depassee, le lot ne se vend plus. DLUO : « de preference
-- avant » — depassee, il se vend encore, avec un avertissement.
alter table article add column type_date varchar(4);
alter table article add constraint ck_article_type_date check (type_date is null or type_date in ('DLC', 'DLUO'));
-- Combien de jours avant la date on veut etre prevenu ; nul, le reglage du magasin.
alter table article add column delai_alerte_peremption integer;
alter table article add constraint ck_article_delai_peremption
    check (delai_alerte_peremption is null or delai_alerte_peremption >= 0);

alter table entreprise add column delai_alerte_peremption integer not null default 30;
alter table entreprise add constraint ck_entreprise_delai_peremption check (delai_alerte_peremption >= 0);

create table lot (
    id                 bigserial primary key,
    id_entreprise      bigint,
    id_article         bigint not null references article (id) on delete cascade,
    numero             varchar(60) not null,
    -- Nulle pour un article suivi sans date : on trace le lot sans qu'il perime.
    date_peremption    date,
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone
);

-- Un numero de lot designe un seul lot de l'article : le fabricant ne le reutilise pas.
create unique index ux_lot_numero on lot (id_article, lower(numero));
create index ix_lot_peremption on lot (id_entreprise, date_peremption);

-- Le lot d'un mouvement. Nul pour les articles non suivis, et pour le stock anterieur au suivi :
-- il se vend en premier, il est le plus ancien.
alter table mvt_stk add column id_lot bigint references lot (id);
-- Le document qui a fait bouger le lot : c'est ce qui permet de retrouver les ventes d'un lot
-- rappele, et de remettre a sa place ce qu'une vente annulee avait sorti.
alter table mvt_stk add column id_vente bigint references vente (id);
alter table mvt_stk add column id_transfert bigint references transfert (id);
create index ix_mvt_stk_lot on mvt_stk (id_lot, id_site);
create index ix_mvt_stk_vente on mvt_stk (id_vente);
create index ix_mvt_stk_transfert on mvt_stk (id_transfert);

-- Le lot recu sur une ligne de reception fournisseur passe par le mouvement ; rien d'autre a
-- stocker sur la ligne de commande.
