-- L'inventaire : compter ce qu'il y a reellement en magasin, et rattraper l'ecart.
--
-- Le stock se deduit de ses mouvements, et cela suffit tant que rien ne se perd. Or il se perd :
-- la casse, le vol, la livraison mal saisie, l'article donne sans ticket. Rien ne permettait de
-- confronter le stock calcule a ce que l'on voit sur l'etagere, ni de le corriger autrement qu'en
-- saisissant des entrees et des sorties a la main, sans trace de la raison.
--
-- Une seance d'inventaire donne cette trace. Elle fige ce que le logiciel croit, recoit ce que
-- l'on compte, et ne touche au stock qu'a la validation — par des mouvements ordinaires portant
-- le motif INVENTAIRE, jamais par une reecriture directe. Le stock reste ainsi ce qu'il a
-- toujours ete : la somme de son histoire.

-- Le rang d'une seance, qui ne se rejoue pas. Comme pour les factures : deux seances ouvertes la
-- meme seconde par deux magasins obtiendraient sinon la meme reference.
create sequence inventaire_reference_seq start with 1 increment by 1;

create table seance_inventaire (
    id                 bigserial primary key,
    reference          varchar(30)  not null,
    date_ouverture     timestamp(6) with time zone not null,
    -- Nulle tant que la seance est ouverte.
    date_cloture       timestamp(6) with time zone,
    statut             varchar(20)  not null,
    commentaire        varchar(255),
    id_entreprise      bigint,
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone,
    constraint ck_seance_inventaire_statut
        check (statut in ('OUVERTE', 'VALIDEE', 'ABANDONNEE')),
    -- Une seance fermee porte sa date de cloture, une seance ouverte n'en a pas. L'un sans
    -- l'autre serait un etat que personne ne saurait lire.
    constraint ck_seance_inventaire_cloture
        check ((statut = 'OUVERTE') = (date_cloture is null))
);

-- Une seule seance ouverte a la fois, par entreprise.
--
-- Deux comptages simultanes figeraient deux fois le meme theorique et produiraient, a la
-- validation, deux corrections dont la seconde defait la premiere. La contrainte le dit ici,
-- plutot qu'un controle en Java que deux requetes simultanees contourneraient.
create unique index ux_seance_inventaire_ouverte
    on seance_inventaire (id_entreprise)
    where statut = 'OUVERTE';

create index ix_seance_inventaire_entreprise on seance_inventaire (id_entreprise, date_ouverture desc);

-- Le comptage, article par article.
--
-- `ligne_comptage` et non `ligne_inventaire` : ce dernier nom designe deja, dans l'application,
-- la vue de valorisation du magasin — combien vaut ce qu'on a — qui ne compte rien du tout.
create table ligne_comptage (
    id                  bigserial primary key,
    id_seance           bigint not null,
    id_article          bigint not null,
    -- Le code et la designation sont recopies a l'ouverture, comme sur une ligne de facture : un
    -- article renomme ou supprime apres coup ne doit pas rendre illisible un inventaire passe.
    code_article        varchar(255),
    designation         varchar(255),
    -- Ce que le logiciel croyait avoir au moment d'ouvrir la seance. Fige : sans quoi l'ecart
    -- affiche bougerait sous les yeux de celui qui compte, a chaque vente du comptoir.
    quantite_theorique  numeric(38, 2) not null,
    -- Ce qu'on a trouve sur l'etagere. Nulle tant que la ligne n'a pas ete comptee — et cette
    -- distinction compte : zero compte veut dire « il n'y en a plus », ce qui n'est pas la meme
    -- chose que « pas encore regarde ».
    quantite_comptee    numeric(38, 2),
    -- Le stock du logiciel a l'instant precis ou la ligne a ete comptee.
    --
    -- C'est lui, et non le theorique fige, qui sert a calculer la correction. Un magasin qui
    -- compte sans fermer sa porte continue de vendre : si l'on corrigeait vers le theorique de
    -- l'ouverture, on effacerait les ventes survenues entre-temps. La correction vaut donc
    -- « compte moins stock au comptage », et les mouvements posterieurs s'appliquent par-dessus,
    -- ce qui est exact dans les deux cas — boutique fermee ou boutique ouverte.
    stock_au_comptage   numeric(38, 2),
    compte_le           timestamp(6) with time zone,
    id_entreprise       bigint,
    creation_date       timestamp(6) with time zone not null,
    last_modified_date  timestamp(6) with time zone,
    constraint fk_ligne_comptage_seance foreign key (id_seance) references seance_inventaire (id),
    constraint fk_ligne_comptage_article foreign key (id_article) references article (id),
    -- Un article ne figure qu'une fois dans une seance : deux lignes pour le meme article
    -- donneraient deux corrections contradictoires.
    constraint ux_ligne_comptage_article unique (id_seance, id_article),
    -- Une ligne comptee porte les trois informations du comptage, ou aucune.
    constraint ck_ligne_comptage_comptee
        check ((quantite_comptee is null) = (compte_le is null)
               and (quantite_comptee is null) = (stock_au_comptage is null)),
    constraint ck_ligne_comptage_quantite check (quantite_comptee is null or quantite_comptee >= 0)
);

create index ix_ligne_comptage_seance on ligne_comptage (id_seance);

-- Le motif des mouvements que la validation produira.
--
-- Sans lui, une correction d'inventaire serait indiscernable d'une saisie manuelle, et l'on ne
-- pourrait plus repondre a la seule question qui vaille quand un stock ne tombe pas juste : d'ou
-- vient cette unite.
alter table mvt_stk
    drop constraint ck_mvt_stk_motif;

alter table mvt_stk
    add constraint ck_mvt_stk_motif
        check (motif is null or motif in
            ('LIVRAISON_COMMANDE', 'VENTE', 'ANNULATION_VENTE', 'CORRECTION_VENTE',
             'SAISIE_MANUELLE', 'INVENTAIRE'));
