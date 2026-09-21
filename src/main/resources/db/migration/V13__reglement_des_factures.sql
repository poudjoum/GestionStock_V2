-- Reglement des factures.
--
-- Une facture etait emise ou annulee, jamais reglee : rien ne disait ce qui avait ete encaisse, ni
-- ce qui restait du. C'est pourtant la question que pose le comptoir tous les jours.
--
-- Un reglement est un mouvement, pas un solde. Le montant paye se somme a la lecture plutot que
-- d'etre tenu a jour sur la facture : une colonne « deja paye » se desynchronise au premier
-- traitement interrompu, une somme de mouvements non. C'est le meme choix que pour le stock.
--
-- Les paiements partiels sont la regle et non l'exception : un client qui laisse un acompte et
-- solde a la livraison est le cas ordinaire.

create table reglement (
    id                 bigserial primary key,
    id_facture         bigint not null,
    date_reglement     timestamp(6) with time zone not null,
    montant            numeric(38, 2) not null,
    mode               varchar(20) not null,
    -- Le numero de transaction mobile money, celui du cheque, la reference du virement : ce par
    -- quoi un encaissement se retrouve aupres de l'etablissement qui l'a recu.
    reference          varchar(255),
    id_entreprise      bigint,
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone,
    constraint fk_reglement_facture foreign key (id_facture) references facture (id),
    constraint ck_reglement_montant check (montant > 0),
    constraint ck_reglement_mode check (mode in
        ('ESPECES', 'MOBILE_MONEY', 'VIREMENT', 'CHEQUE', 'AUTRE'))
);

create index ix_reglement_facture on reglement (id_facture);
create index ix_reglement_entreprise on reglement (id_entreprise);
