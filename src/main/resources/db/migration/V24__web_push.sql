-- Web Push : prevenir un appareil, meme quand l'application est fermee.
--
-- Une notification de l'application ne se lisait qu'en ouvrant l'application : l'alerte de
-- rupture attendait que le magasinier passe devant son ecran. Un appareil abonne la recoit
-- desormais tout de suite, par le service push de son navigateur.
--
-- Un abonnement est un appareil — un navigateur sur un telephone ou un poste — et non un compte :
-- le meme gerant peut etre prevenu sur son telephone et sur la caisse. Il appartient au compte qui
-- s'y est connecte en dernier.

create table abonnement_push
(
    id                 bigserial primary key,
    id_utilisateur     bigint                      not null,
    id_entreprise      bigint,
    -- L'adresse du service push ou deposer les messages. Elle identifie l'appareil : un meme
    -- navigateur qui se reabonne rend la meme.
    adresse            varchar(1000)               not null,
    -- De quoi chiffrer pour ce seul navigateur (RFC 8291). Le service push transporte des
    -- messages qu'il ne peut pas lire.
    cle_p256dh         varchar(120)                not null,
    cle_auth           varchar(40)                 not null,
    -- Ce que l'appareil a dit de lui, pour que la personne reconnaisse ses appareils.
    appareil           varchar(200),
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone,

    constraint uq_abonnement_push_adresse unique (adresse),
    constraint fk_abonnement_push_utilisateur
        foreign key (id_utilisateur) references utilisateur (id) on delete cascade
);

create index ix_abonnement_push_utilisateur on abonnement_push (id_utilisateur);

-- Le secret d'un envoi push n'est pas dans la ligne : il est chiffre au moment de partir. Le
-- corps garde le message en clair, comme pour un courriel, et la destination designe
-- l'abonnement par son identifiant.
