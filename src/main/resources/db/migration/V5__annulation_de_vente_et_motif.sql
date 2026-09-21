-- Deux ajouts lies : une vente peut desormais etre annulee, et tout mouvement de stock dit
-- pourquoi il a eu lieu.
--
-- Le motif devient necessaire du moment qu'une vente se corrige : annuler une vente de 15 remet
-- 15 en magasin, et cette entree est indiscernable d'une livraison si rien ne la distingue. Or
-- c'est exactement ce qu'on cherche a comprendre quand un stock ne tombe pas juste.

alter table vente
    add column annulee boolean not null default false;

-- Nullable, et c'est voulu : les mouvements anterieurs n'ont pas de motif connu, et leur en
-- inventer un serait pire que de reconnaitre qu'on l'ignore.
alter table mvt_stk
    add column motif varchar(40);

alter table mvt_stk
    add constraint ck_mvt_stk_motif
        check (motif is null or motif in
            ('LIVRAISON_COMMANDE', 'VENTE', 'ANNULATION_VENTE', 'CORRECTION_VENTE', 'SAISIE_MANUELLE'));
