-- Amorcage du super-administrateur.
--
-- Le role existe depuis la V10, mais personne ne le porte : sur une installation deja en service,
-- aucun compte ne peut donc creer d'entreprise, et le rattachement d'un compte resterait a faire
-- en SQL — exactement ce qu'on cherche a supprimer.
--
-- La promotion vise le compte le plus ancien *non rattache a une entreprise* : c'est celui qui a
-- installe l'application, et non l'employe d'un client. Un compte rattache ne remonte jamais au
-- rang au-dessus des entreprises par cette migration.
--
-- Si un super-administrateur existe deja, ou si tous les comptes appartiennent a une entreprise,
-- rien ne se passe : mieux vaut ne promouvoir personne que de promouvoir le mauvais.

insert into user_roles (user_id, role_id)
select u.id, r.id
from utilisateur u
         cross join role r
where r.role_name = 'ROLE_SUPER_ADMIN'
  and u.id_entreprise is null
  and u.id = (select min(id) from utilisateur where id_entreprise is null)
  and not exists (select 1
                  from user_roles ur
                           join role r2 on r2.id = ur.role_id
                  where r2.role_name = 'ROLE_SUPER_ADMIN')
  and not exists (select 1 from user_roles ur2 where ur2.user_id = u.id and ur2.role_id = r.id);
