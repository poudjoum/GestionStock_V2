-- Seuil d'alerte par article.
--
-- Le stock se lisait article par article, sans que rien ne signale ceux qui s'epuisent : la
-- rupture se decouvrait au comptoir, devant le client. Le seuil dit a partir de quelle quantite
-- il faut recommander.
--
-- Nullable, et c'est voulu : tous les articles ne meritent pas une alerte, et imposer un seuil
-- par defaut noierait les vraies sous des dizaines de fausses.

alter table article
    add column seuil_alerte numeric(38, 2);

alter table article
    add constraint ck_article_seuil_alerte check (seuil_alerte is null or seuil_alerte >= 0);
