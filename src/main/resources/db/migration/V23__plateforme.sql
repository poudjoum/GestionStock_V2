-- La plateforme : ce qui est vrai au-dessus des entreprises, et non a l'interieur de chacune.
--
-- L'application savait tenir un magasin. Elle ne savait pas qu'elle en hebergeait plusieurs : rien
-- ne permettait d'en inscrire un depuis l'ecran, de voir ce que chacun fait, ni de fermer l'acces
-- a celui qui ne paie plus. C'etait pourtant le projet — vendre cet outil aux petits commerces par
-- abonnement.

-- Le mot de passe donne a l'inscription est provisoire.
--
-- L'editeur le choisit, l'envoie au gerant par courriel, et ne doit pas le connaitre plus
-- longtemps que cela. Ce drapeau force le changement avant toute autre chose, et c'est ce qui rend
-- acceptable de l'avoir transmis en clair.
alter table utilisateur
    add column motdepasse_a_changer boolean not null default false;

-- L'echeance de l'abonnement annuel.
--
-- Une date et non un compteur de jours : « jusqu'au 31 octobre » se verifie d'un coup d'oeil et ne
-- derive pas, la ou un compteur doit etre decremente par quelqu'un et se desynchronise des que ce
-- quelqu'un tombe en panne.
--
-- Nulle pour un commerce qui n'a pas encore d'abonnement — les entreprises deja en base, et celles
-- qu'on inscrit pour essayer.
alter table entreprise
    add column abonnement_echeance date;

-- L'acces d'un commerce, coupe d'un geste.
--
-- Independant de l'echeance, et c'est voulu : une echeance depassee fait passer le commerce en
-- « echu » sur le tableau de bord de l'editeur, elle ne ferme rien d'elle-meme. Couper la caisse
-- d'un commercant le jour anniversaire, sans que personne ait regarde, fermerait une boutique pour
-- un virement en retard de deux jours. La decision reste humaine ; l'ecran la rend visible.
--
-- Suspendre plutot que supprimer : un abonnement repris doit retrouver son magasin intact, ses
-- ventes, son stock et ses comptes. La suspension ferme la porte, elle n'efface rien.
alter table entreprise
    add column suspendue boolean not null default false;

-- Quand la porte a ete fermee la derniere fois. Elle survit a la reprise : savoir qu'un commerce a
-- deja ete suspendu fait partie de son histoire.
alter table entreprise
    add column suspendue_le timestamp(6) with time zone;

-- Un message dont le corps ne doit pas survivre a son envoi.
--
-- La file garde ce qu'elle a livre — c'est ce qui permet de savoir qui a recu quoi. Mais le
-- courriel d'inscription porte un mot de passe : le laisser en base le rendrait lisible pour
-- toujours a qui ouvre la table, longtemps apres que le gerant l'a change. Marque ainsi, son corps
-- est efface des qu'il est parti ; la ligne, elle, reste, avec sa destination et sa date.
alter table envoi
    add column sensible boolean not null default false;
