-- La TVA se parametre sur l'entreprise.
--
-- Elle n'existait que sur l'article, ou il fallait la redire a chaque creation. Surtout, rien ne
-- permettait de dire qu'une entreprise n'est pas assujettie : toutes les entreprises collectaient
-- la TVA, ce qui est faux — certaines la reversent aux impots par declaration, d'autres n'y sont
-- pas soumises du tout.
--
-- 19,25 % par defaut : le taux en vigueur au Cameroun. Les entreprises deja enregistrees le
-- recoivent, ce qui reproduit ce que faisaient leurs articles.

alter table entreprise
    add column assujettie_tva boolean not null default true;

alter table entreprise
    add column taux_tva numeric(38, 2) not null default 19.25;

alter table entreprise
    add constraint ck_entreprise_taux_tva check (taux_tva >= 0 and taux_tva <= 100);

-- La facture dit si la TVA etait applicable. Sans cela, un total de TVA a zero ne se distingue
-- pas d'une facture ou personne n'a su la calculer — et c'est une mention qui doit figurer sur le
-- document remis au client.
alter table facture
    add column tva_applicable boolean not null default true;
