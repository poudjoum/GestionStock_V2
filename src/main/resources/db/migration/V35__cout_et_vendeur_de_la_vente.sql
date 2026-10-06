-- Le reporting : la marge, et qui a vendu.
--
-- Le cout d'achat moyen d'un article bouge a chaque livraison. Calculer la marge d'une vente de
-- mars avec le cout d'aujourd'hui donnerait un chiffre faux, qui changerait encore a la prochaine
-- livraison. Le cout est donc fige sur la ligne au moment de la vente, comme la contenance d'un
-- carton : par unite de base, nul pour les ventes d'avant — leur marge n'est qu'estimee.

alter table ligne_vente add column cout_unitaire numeric(19, 4);

-- Le compte qui a enregistre la vente. Nul pour les ventes d'avant : on ne le savait pas.
-- Sans cle etrangere : une trace, pas un lien — elle ne doit empecher ni une vente faite par un
-- compte d'ailleurs (une synchronisation), ni la suppression un jour d'un compte qui a vendu.
alter table vente add column id_vendeur bigint;

-- Les rapports lisent les ventes d'une entreprise sur une periode.
create index if not exists ix_vente_entreprise_date on vente (id_entreprise, date_vente);
