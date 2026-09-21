-- Socle du cloisonnement par entreprise.
--
-- `id_entreprise` existe depuis le premier jour sur presque toutes les tables, sans que rien ne
-- le renseigne ni ne filtre dessus : deux entreprises partageant cette base verraient les
-- articles, les clients et les ventes l'une de l'autre.

-- Un role au-dessus des entreprises. ADMIN administre la sienne ; le super-administrateur est
-- celui qui les cree et qui peut regarder au-dela — c'est l'editeur, pas le gerant.
alter table role drop constraint if exists ck_role_role_name;
alter table role
    add constraint ck_role_role_name check (role_name in
        ('ROLE_USER', 'ROLE_CAISSIER', 'ROLE_ADMIN', 'ROLE_COMPTABLE', 'ROLE_MANAGER',
         'ROLE_MAGASINIER', 'ROLE_SUPER_ADMIN'));

insert into role (role_name, creation_date)
select 'ROLE_SUPER_ADMIN', now()
where not exists (select 1 from role where role_name = 'ROLE_SUPER_ADMIN');

-- `mvt_stk.id_entreprise` etait un entier la ou toutes les autres tables portent un bigint : le
-- champ est un Integer dans l'entite. Un cloisonnement qui compare des identifiants ne peut pas
-- vivre avec deux types selon la table.
alter table mvt_stk
    alter column id_entreprise type bigint;

-- Des index sur la colonne de cloisonnement : desormais, presque toutes les lectures filtrent
-- dessus.
create index ix_article_entreprise on article (id_entreprise);
create index ix_client_entreprise on client (id_entreprise);
create index ix_fournisseur_entreprise on fournisseur (id_entreprise);
create index ix_vente_entreprise on vente (id_entreprise);
create index ix_facture_entreprise on facture (id_entreprise);
create index ix_commande_client_entreprise on commande_client (id_entreprise);
create index ix_commande_fournisseur_entreprise on commande_fournisseur (id_entreprise);
create index ix_mvt_stk_entreprise on mvt_stk (id_entreprise);
create index ix_category_entreprise on category (entreprise_id);
