-- Ce que le ticket de caisse imprime en en-tete.
--
-- L'entreprise savait dire son nom, son adresse, son telephone et son registre de commerce. Il
-- lui manquait les deux mentions que le client cherche sur un ticket camerounais : le NIU, qui
-- identifie l'entreprise aupres des impots et accompagne toute facture d'un assujetti, et le
-- logo, qui fait reconnaitre le magasin d'un coup d'oeil.

alter table entreprise
    add column niu varchar(30);

-- Le logo est stocke ici, encode, et non par une adresse web.
--
-- Une URL suppose que quelqu'un heberge l'image et continue de le faire : le jour ou elle ne
-- repond plus, les tickets sortent avec un carre vide, et personne ne sait pourquoi. Une image
-- en base part avec la sauvegarde, s'imprime sans reseau — ce qui compte pour une caisse — et ne
-- depend de personne.
--
-- Le navigateur la reduit a 384 pixels de large avant de l'envoyer, la largeur exacte de la tete
-- d'une imprimante 80 mm a 203 points par pouce. Au-dela, on stockerait des pixels que le papier
-- ne peut pas rendre. Quelques kilo-octets suffisent donc, et la contrainte ci-dessous ferme la
-- porte a l'envoi d'une photo entiere.
alter table entreprise
    add column logo text;

alter table entreprise
    add constraint ck_entreprise_logo check (logo is null or logo like 'data:image/%');
