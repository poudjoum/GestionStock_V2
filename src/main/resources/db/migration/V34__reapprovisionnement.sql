-- Le reapprovisionnement : combien de jours de ventes le magasin veut avoir devant lui.
--
-- La proposition de commande couvre ce nombre de jours, au rythme des ventes des trente derniers
-- jours, plus le seuil d'alerte en securite, moins ce qui est disponible et ce qui est deja
-- commande. Quinze jours : deux livraisons par mois, le rythme ordinaire d'un fournisseur.
--
-- Le stock reserve n'a pas de table : il se lit dans les lignes des commandes clients validees,
-- comme le stock dans ses mouvements.

alter table entreprise add column jours_couverture integer not null default 15;
alter table entreprise add constraint ck_entreprise_jours_couverture check (jours_couverture between 1 and 365);

-- Les ventes d'une periode se relisent par site et par date pour le rythme de vente.
create index if not exists ix_mvt_stk_site_date on mvt_stk (id_site, date_mvt);
