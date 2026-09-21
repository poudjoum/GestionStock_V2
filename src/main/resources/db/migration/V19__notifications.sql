-- Notifications : ce qu'on lit dans l'application, et ce qui doit en sortir.
--
-- Deux tables, parce que ce sont deux choses. Une notification est adressee a un utilisateur et
-- se lit dans l'application : elle a un etat « lu ». Un envoi quitte l'application — un courriel
-- au client qui recoit sa facture, et ce client n'a pas de compte : il a un etat « envoye » et
-- des tentatives. Les confondre obligerait a porter les deux jeux de colonnes sur chaque ligne,
-- dont la moitie serait toujours nulle.
--
-- La regle qui tient l'ensemble : un echec d'envoi ne doit jamais faire echouer une operation
-- metier. Une vente ne se refuse pas parce que le serveur SMTP est tombe. On enregistre donc
-- l'intention de notifier dans la transaction de l'operation, et on la livre separement. C'est
-- le « mouvement plutot que solde » applique aux envois : la file est la trace, la livraison
-- vient apres.

create table notification
(
    id                 bigserial primary key,
    id_destinataire    bigint                      not null,
    id_entreprise      bigint,
    type               varchar(40)                 not null,
    titre              varchar(200)                not null,
    corps              text,
    -- Ou aller dans l'application en cliquant dessus. Un chemin, jamais une URL absolue : le
    -- serveur ne sait pas sous quel domaine le front est servi.
    lien               varchar(300),
    -- De quoi eviter de repeter la meme alerte. Un article sous son seuil le reste a chaque
    -- vente ; sans cette cle, le magasinier recevrait une notification par article vendu.
    cle                varchar(120),
    lu_le              timestamp(6) with time zone,
    creation_date      timestamp(6) with time zone not null,
    last_modified_date timestamp(6) with time zone,

    constraint fk_notification_destinataire
        foreign key (id_destinataire) references utilisateur (id)
);

-- La liste d'un utilisateur, et son compte de non lues : les deux seules lectures.
create index ix_notification_destinataire on notification (id_destinataire, lu_le);
-- La recherche de doublon avant d'ecrire une alerte.
create index ix_notification_cle on notification (id_entreprise, cle) where lu_le is null;

create table envoi
(
    id                  bigserial primary key,
    canal               varchar(20)                 not null,
    destination         varchar(320)                not null,
    sujet               varchar(300)                not null,
    corps               text                        not null,
    etat                varchar(20)                 not null,
    tentatives          integer                     not null default 0,
    -- A partir de quand reessayer. Une adresse momentanement injoignable ne doit pas etre
    -- retentee en boucle : l'attente double a chaque echec.
    prochaine_tentative timestamp(6) with time zone not null,
    derniere_erreur     varchar(500),
    envoye_le           timestamp(6) with time zone,
    id_entreprise       bigint,
    creation_date       timestamp(6) with time zone not null,
    last_modified_date  timestamp(6) with time zone,

    constraint ck_envoi_etat check (etat in ('A_ENVOYER', 'ENVOYE', 'ABANDONNE'))
);

-- Ce que l'expediteur cherche a chaque passage : ce qui attend, et dont l'heure est venue.
create index ix_envoi_a_traiter on envoi (etat, prochaine_tentative);
