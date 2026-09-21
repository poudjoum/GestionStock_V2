-- Le client appartient a la vente.
--
-- Il n'etait accessible qu'a travers la commande servie, et une vente au comptoir ne pouvait donc
-- jamais etre nominative : vendre a quelqu'un de connu obligeait a ouvrir une commande dont on
-- n'avait pas besoin. Le lien direct supprime ce detour.
--
-- Nullable : la plupart des ventes de comptoir n'ont pas de client, et c'est tres bien ainsi.

alter table vente
    add column id_client bigint;

alter table vente
    add constraint fk_vente_client foreign key (id_client) references client (id);

-- Les ventes deja issues d'une commande connaissent leur client par elle : on le recopie, pour
-- que la lecture n'ait plus qu'un seul chemin a suivre.
update vente v
set id_client = cc.id_client
from commande_client cc
where v.id_commande_client = cc.id
  and cc.id_client is not null;
