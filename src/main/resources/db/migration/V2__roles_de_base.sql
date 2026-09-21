-- Les six roles que le code connait (enumeration ERole).
--
-- Rien ne les creait : /api/auth/signup les cherche en base et echoue sur « Error: Role is not
-- found » tant qu'ils manquent. Sur une base vide, aucune inscription n'etait donc possible — et
-- sans inscription, aucune connexion.
--
-- Le `where not exists` protege une base qui en contiendrait deja (reprise d'une installation
-- anterieure par `flyway.baseline-on-migrate`) : le code lit un role par son nom et attend une
-- ligne unique.
insert into role (role_name, creation_date)
select r.nom, now()
from (values ('ROLE_USER'),
             ('ROLE_ADMIN'),
             ('ROLE_MANAGER'),
             ('ROLE_CAISSIER'),
             ('ROLE_COMPTABLE'),
             ('ROLE_MAGASINIER')) as r(nom)
where not exists (select 1 from role existant where existant.role_name = r.nom);
