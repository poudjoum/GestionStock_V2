-- Les resumes par courriel : le gerant recoit ses chiffres sans ouvrir l'application.
--
-- Chaque lundi matin, la semaine ecoulee ; chaque matin, la veille, si le commerce le demande.

-- La version mise en forme d'un courriel ; nulle, il part en texte seul comme avant. Le texte
-- reste toujours : c'est ce que lisent les messageries qui n'affichent pas le HTML.
alter table envoi add column corps_html text;

alter table entreprise add column resume_hebdo boolean not null default true;
alter table entreprise add column resume_quotidien boolean not null default false;

-- Ce qui est deja parti : un serveur qui redemarre a 7 h 01 ne renvoie pas le resume de 7 h.
create table resume_envoye (
    id            bigserial primary key,
    id_entreprise bigint not null references entreprise (id) on delete cascade,
    type          varchar(12) not null,
    debut         date not null,
    envoye_le     timestamp(6) with time zone not null,
    constraint ck_resume_envoye_type check (type in ('QUOTIDIEN', 'HEBDO')),
    constraint ux_resume_envoye unique (id_entreprise, type, debut)
);
