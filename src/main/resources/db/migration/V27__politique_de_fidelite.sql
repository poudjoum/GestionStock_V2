-- La politique de fidelite de chaque magasin, reglee par son gerant.
--
-- `montant_par_point` (V25) dit ce qu'il faut depenser pour gagner un point. Restait a dire ce
-- qu'un point vaut quand on l'echange : la conversion etait figee a un franc le point pour tous
-- les magasins, et un bon pouvait se demander pour un seul point.
--
-- Les valeurs par defaut reprennent ce qui se faisait jusqu'ici : 1 F le point, 90 jours de
-- validite. Le minimum de 1000 points est celui de l'exemple du cahier des charges.
alter table entreprise add column valeur_point_fcfa numeric(12, 2) not null default 1;
alter table entreprise add column points_minimum_bon integer not null default 1000;
alter table entreprise add column duree_validite_bon_jours integer not null default 90;

alter table entreprise add constraint ck_entreprise_valeur_point check (valeur_point_fcfa > 0);
alter table entreprise add constraint ck_entreprise_points_minimum_bon check (points_minimum_bon >= 1);
alter table entreprise add constraint ck_entreprise_duree_validite_bon check (duree_validite_bon_jours >= 1);
