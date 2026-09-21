-- Le jeton qui permet d'en obtenir un autre.
--
-- Un JWT ne se revoque pas : signe, il vaut jusqu'a son expiration, et fermer un compte ne le
-- rappelle pas. Le jeton d'acces durait 24 h, faute de pouvoir le renouveler autrement qu'en se
-- reconnectant — un employe renvoye gardait donc ses acces jusqu'au lendemain.
--
-- Celui-ci vit en base, donc se revoque. Il ne porte aucun droit : il ne sert qu'a redemander un
-- jeton d'acces, et chaque echange le remplace par un nouveau.

create table jeton_rafraichissement
(
    id                 bigserial primary key,
    jeton              varchar(64)                 not null,
    id_utilisateur     bigint                      not null,
    expiration         timestamp(6) with time zone not null,
    -- Revoque plutot que supprime : un jeton revoque qu'on represente est le signe qu'il a ete
    -- copie, et l'effacer effacerait la seule trace permettant de s'en apercevoir.
    revoque_le         timestamp(6) with time zone,
    -- Pourquoi il a cesse de valoir, parce que cela decide de ce qui se passe s'il revient.
    -- Un jeton remplace par rotation ne doit jamais revenir : s'il revient, une copie circule et
    -- tout le compte se ferme. Un jeton deconnecte qu'on rejoue est une maladresse de client, et
    -- fermer tout le compte pour cela couperait la caisse restee ouverte au comptoir.
    motif_revocation   varchar(20),
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone,

    constraint fk_jeton_rafraichissement_utilisateur
        foreign key (id_utilisateur) references utilisateur (id),
    constraint ck_jeton_rafraichissement_motif
        check (motif_revocation is null
               or motif_revocation in ('DECONNEXION', 'ROTATION', 'SECURITE')),
    -- Un jeton est revoque avec son motif, ou ne l'est pas du tout : l'un sans l'autre serait une
    -- ligne qu'on ne saurait plus interpreter.
    constraint ck_jeton_rafraichissement_revocation
        check ((revoque_le is null) = (motif_revocation is null))
);

create unique index ux_jeton_rafraichissement_jeton on jeton_rafraichissement (jeton);

-- Revoquer les jetons d'un compte ferme, ou retrouver ceux qui restent a nettoyer.
create index ix_jeton_rafraichissement_utilisateur on jeton_rafraichissement (id_utilisateur);
