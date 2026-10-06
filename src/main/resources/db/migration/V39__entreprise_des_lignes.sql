-- Les lignes de vente creees au comptoir ou par la synchronisation, et les lignes saisies avec
-- une commande client, n'avaient pas leur entreprise : elles la prennent de leur document. Rien ne
-- les lisait sans passer par lui, mais une ligne sans entreprise echappe au cloisonnement le jour
-- ou quelqu'un la lit seule.

update ligne_vente l
   set id_entreprise = v.id_entreprise
  from vente v
 where v.id = l.id_vente
   and l.id_entreprise is null
   and v.id_entreprise is not null;

update ligne_cmnde_client l
   set id_entreprise = c.id_entreprise
  from commande_client c
 where c.id = l.id_commande_client
   and l.id_entreprise is null
   and c.id_entreprise is not null;
