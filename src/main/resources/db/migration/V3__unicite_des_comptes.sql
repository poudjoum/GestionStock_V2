-- Unicite de l'identifiant et de l'adresse de courriel d'un compte.
--
-- AuthControler verifiait deja l'absence de doublon avant d'inserer, mais en Java : entre le
-- `existsByUsername` et le `save`, une seconde requete peut passer et les deux comptes sont
-- ecrits. Seule la base peut trancher, parce qu'elle seule voit les deux insertions.
--
-- PostgreSQL admet plusieurs NULL dans un index unique : les comptes sans courriel restent donc
-- possibles, sans se bloquer les uns les autres.
--
-- Ces index echoueront si la base contient deja des doublons. C'est voulu : la migration doit
-- s'arreter et laisser quelqu'un decider lequel des deux comptes garder, plutot que d'en
-- supprimer un toute seule.
create unique index ux_utilisateur_username on utilisateur (username);
create unique index ux_utilisateur_email on utilisateur (email);
