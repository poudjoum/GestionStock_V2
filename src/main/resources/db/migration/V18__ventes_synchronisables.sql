-- Rendre une vente synchronisable depuis un poste hors ligne.
--
-- Trois choses manquaient pour qu'un caissier puisse vendre sans reseau et envoyer la vente a la
-- reconnexion.
--
-- 1. Une identite fournie par le client. Un telephone qui perd le reseau au milieu d'un envoi ne
--    sait pas si la vente est passee : il reessaie. Sans identite venue de lui, on obtient deux
--    ventes et une double sortie de stock. L'identifiant de base ne peut pas servir a cela — il
--    n'existe qu'une fois la vente ecrite, donc trop tard.
--
-- 2. La date reelle. `date_vente` etait posee par le serveur a l'enregistrement : une vente de
--    9 h synchronisee a midi devenait une vente de midi, et l'etat de caisse du jour devenait
--    faux.
--
-- 3. Accepter que le stock passe sous zero. Deux caisses vendent hors ligne le dernier sac de
--    ciment ; la seconde synchronise et se fait refuser, alors que le sac est parti. Cela ne se
--    regle pas en base mais dans le service — c'est note ici parce que la lecture de ce fichier
--    est le moment ou l'on se demande pourquoi.

alter table vente
    add column reference_client varchar(64);

-- Unique globalement, et non par entreprise : un UUID l'est par construction, et une unicite par
-- entreprise laisserait passer deux lignes de meme reference des que l'entreprise est nulle —
-- PostgreSQL tient deux NULL pour distincts.
create unique index ux_vente_reference_client on vente (reference_client);
