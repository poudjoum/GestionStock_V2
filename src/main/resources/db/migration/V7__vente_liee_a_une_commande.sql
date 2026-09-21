-- La vente qui sert une commande client, et le client sur la facture.
--
-- Une vente ne connaissait pas son client : seule la commande client le designe. La facture ne
-- pouvait donc etre que anonyme, ce qui la rendait inutilisable des qu'il faut la remettre a
-- quelqu'un.
--
-- Le lien est nullable : une vente au comptoir — le cas du supermarche — n'a pas de commande
-- derriere elle, et c'est parfaitement legitime.

alter table vente
    add column id_commande_client bigint;

alter table vente
    add constraint fk_vente_commande_client
        foreign key (id_commande_client) references commande_client (id);

-- Une commande ne se sert qu'une fois : deux ventes sur la meme commande sortiraient deux fois la
-- marchandise. PostgreSQL admettant plusieurs NULL dans un index unique, les ventes au comptoir
-- ne se genent pas entre elles.
create unique index ux_vente_commande_client on vente (id_commande_client);

-- Le nom est recopie, comme le reste de la facture : un client renomme ou supprime ne doit pas
-- changer un document deja remis. L'identifiant est conserve a cote pour retrouver la fiche.
alter table facture
    add column id_client bigint;

alter table facture
    add column nom_client varchar(255);

alter table facture
    add constraint fk_facture_client foreign key (id_client) references client (id);
