-- Le suivi des commerces par l'editeur : qui se sert encore de l'outil.
--
-- La derniere activite d'un compte est notee a la connexion et a chaque renouvellement de sa
-- session : une caisse ouverte toute la semaine se renouvelle chaque jour, elle compte donc comme
-- active sans qu'on lui demande de se reconnecter. Nulle pour les comptes d'avant.

alter table utilisateur add column derniere_activite timestamp(6) with time zone;
