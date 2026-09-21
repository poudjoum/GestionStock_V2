-- Un compte se desactive, il ne se supprime pas.
--
-- Supprimer un compte efface qui a fait quoi : l'employe parti reste l'auteur des ventes qu'il a
-- saisies, et sa fiche doit pouvoir se relire. La desactivation ferme l'acces sans rien effacer.
--
-- Vrai par defaut : les comptes existants restent utilisables.

alter table utilisateur
    add column actif boolean not null default true;
