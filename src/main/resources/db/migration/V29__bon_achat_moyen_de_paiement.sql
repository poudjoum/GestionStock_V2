-- Le bon d'achat devient un moyen de paiement : il diminue le reste a payer d'une facture.
--
-- Le reglement porte le code du bon dans sa reference : c'est par lui qu'on retrouve le bon
-- quand le reglement est repris, pour le rendre utilisable.
alter table reglement drop constraint ck_reglement_mode;
alter table reglement add constraint ck_reglement_mode check (mode in
    ('ESPECES', 'MOBILE_MONEY', 'VIREMENT', 'CHEQUE', 'BON_ACHAT', 'AUTRE'));
