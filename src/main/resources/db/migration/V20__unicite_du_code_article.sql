-- Le code d'un article devient une cle : unique dans une entreprise.
--
-- Il ne l'etait pas, et cela n'avait pas de consequence tant qu'on saisissait les articles a la
-- main : on retrouvait un article par son nom, dans une liste. Deux usages en font desormais une
-- cle.
--
-- La douchette, d'abord. Elle ne rend qu'un code, et rien d'autre : si deux articles le portent,
-- le comptoir en pose un au hasard dans le panier. L'erreur ne se voit pas a l'ecran, elle se
-- decouvre a l'inventaire.
--
-- L'import du catalogue ensuite. Reimporter un fichier corrige doit mettre a jour les articles
-- deja la plutot que de les doubler, et c'est le code qui dit lesquels. Sans unicite, un import
-- rejoue fabrique des doublons a chaque passage.
--
-- Index partiel, et c'est voulu : les donnees anterieures au cloisonnement n'ont pas d'entreprise,
-- et certains articles n'ont pas de code. Les contraindre reviendrait a refuser une migration pour
-- des lignes que personne ne scannera jamais. Ce qui entre desormais, lui, est contraint.
--
-- Cette migration echoue si deux articles d'une meme entreprise portent deja le meme code. C'est
-- le comportement voulu : il n'y a pas de bonne facon de deviner lequel des deux le caissier
-- voulait vendre. Le cas se regle en base, en renommant l'un des deux, avant de rejouer.

create unique index ux_article_code_par_entreprise
    on article (id_entreprise, code_article)
    where id_entreprise is not null
      and code_article is not null;
