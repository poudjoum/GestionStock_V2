# GestionDeStock

API REST de gestion de stock (Spring Boot 3.5, Java 17, PostgreSQL) : articles, categories, clients,
fournisseurs, commandes client et fournisseur, ventes, utilisateurs et roles, avec authentification
par jeton JWT et documentation Swagger.

## Demarrage

Il faut un JDK 17 et Docker. Maven n'est pas necessaire : le depot porte son wrapper (`mvnw`).

```bash
cp .env.example .env     # puis renseignez DB_PASSWORD et JWT_SECRET
docker compose up -d     # PostgreSQL sur 5432, Adminer sur http://localhost:8081
./mvnw spring-boot:run
```

Sous PowerShell, les variables de `.env` ne sont pas chargees toutes seules :

```powershell
Get-Content .env | Where-Object { $_ -match '^\s*[A-Za-z_]+=' } |
  ForEach-Object { $k,$v = $_ -split '=',2; Set-Item -Path "env:$($k.Trim())" -Value $v.Trim() }
.\mvnw.cmd spring-boot:run
```

L'API ecoute sur `http://localhost:9092`, Swagger UI sur
`http://localhost:9092/swagger-ui/index.html`, et les chemins metier sont prefixes par
`/gestiondestock/v1`.

Si un PostgreSQL tourne deja sur la machine, changez `DB_PORT` **et** le port de `DB_URL` dans
`.env` — les deux doivent s'accorder.

## Configuration

Aucun secret n'est ecrit dans le depot : `application.yml` ne contient que des references a des
variables d'environnement, decrites dans `.env.example`. `.env` n'est pas versionne.

`DB_PASSWORD` et `JWT_SECRET` n'ont pas de valeur de repli : sans elles, l'application refuse de
demarrer. C'est voulu — une application qui demarre sur un secret par defaut signe des jetons que
n'importe qui peut refabriquer.

> **Les secrets de l'ancienne configuration sont compromis.** Le mot de passe de la base, le secret
> JWT et les quatre cles Flickr ont ete versionnes en clair dans `src/main/resources/application.yml`
> et restent lisibles dans l'historique Git, y compris apres leur retrait. Ils sont a renouveler chez
> leur emetteur : nouvelles cles Flickr, nouveau mot de passe sur toute base ou l'ancien servait.

## Base de donnees

Le schema appartient aux migrations Flyway de `src/main/resources/db/migration/`, appliquees au
demarrage :

- `V1__init.sql` — les quatorze tables.
- `V2__roles_de_base.sql` — les six roles de `ERole`. Sans eux, `/api/auth/signup` echoue sur
  « Error: Role is not found » et aucun compte ne peut etre cree.
- `V3__unicite_des_comptes.sql` — identifiant et courriel uniques. La verification existait en
  Java avant insertion, mais entre le controle et l'ecriture une seconde requete passe : seule la
  base voit les deux insertions. Un doublon rend un **409**.
- `V4__etat_des_commandes.sql` — le cycle de vie des commandes. Les commandes fournisseur
  anterieures y naissent `LIVREE` et non `EN_PREPARATION` : elles ont ete saisies quand
  l'enregistrement faisait entrer la marchandise, leur stock est donc deja compte, et les livrer a
  nouveau le doublerait.
- `V5__annulation_de_vente_et_motif.sql` — l'annulation d'une vente, et le motif porte par chaque
  mouvement de stock. Le motif est nullable : les mouvements anterieurs n'en ont pas, et leur en
  inventer un serait pire que de reconnaitre qu'on l'ignore.
- `V6__facturation.sql` — factures, lignes de facture et la sequence des numeros.
- `V7__vente_liee_a_une_commande.sql` — le lien entre une vente et la commande qu'elle sert, et le
  client sur la facture. Le lien est nullable — une vente au comptoir n'a pas de commande derriere
  elle — mais unique : deux ventes sur la meme commande sortiraient deux fois la marchandise.
- `V8__client_sur_la_vente.sql` — le client rattache a la vente elle-meme. Les ventes deja issues
  d'une commande y recopient le sien, pour que la lecture n'ait qu'un seul chemin a suivre.
- `V9__tva_de_l_entreprise.sql` — le regime de TVA sur l'entreprise, et la mention correspondante
  sur la facture. Les entreprises deja enregistrees recoivent 19,25 % et restent assujetties, ce
  qui reproduit ce que faisaient leurs articles.
- `V10__cloisonnement_par_entreprise.sql` — le role `ROLE_SUPER_ADMIN`, les index sur la colonne de
  cloisonnement, et `mvt_stk.id_entreprise` passe en `bigint` : c'etait la seule table a porter un
  entier la ou toutes les autres ont un `bigint`, et un cloisonnement qui compare des identifiants
  ne peut pas vivre avec deux types.
- `V11__comptes_actifs.sql` — l'etat ouvert ou ferme d'un compte. Les comptes existants restent
  ouverts.
- `V12__amorcage_super_admin.sql` — promeut le compte le plus ancien non rattache a une entreprise,
  s'il n'existe aucun super-administrateur. Sans lui, une installation deja en service n'aurait
  personne pour ouvrir une entreprise.
- `V13__reglement_des_factures.sql` — les encaissements. Un reglement est un mouvement, pas un
  solde : ce qui a ete paye se somme a la lecture.
- `V14__seuil_d_alerte.sql` — la quantite sous laquelle un article est signale. Nullable : tous
  n'ont pas a etre surveilles.
- `V15__livraison_partielle.sql` — la quantite livree par ligne et l'etat `PARTIELLEMENT_LIVREE`.
  Les lignes des commandes deja livrees y sont marquees livrees en entier, sans quoi elles
  paraitraient attendre encore toute leur marchandise — et l'on pourrait la recevoir une seconde
  fois.
- `V16__cloture_des_reliquats.sql` — l'etat `CLOTUREE` et le motif qui l'accompagne, pour solder
  une commande dont le reste n'arrivera jamais.
- `V17__jetons_de_rafraichissement.sql` — le jeton qui permet d'en obtenir un autre. Il porte le
  motif de sa revocation, parce que c'est lui qui decide de ce qui se passe si ce jeton revient.
- `V18__ventes_synchronisables.sql` — la reference que le poste de vente donne a une vente avant
  de l'envoyer, unique, pour qu'un envoi rejoue ne vende pas deux fois.
- `V19__notifications.sql` — ce qu'on lit dans l'application, et ce qui doit en sortir. Deux
  tables : une notification a un etat « lu », un envoi a des tentatives.

`spring.jpa.hibernate.ddl-auto` vaut `validate` : une entite modifiee sans migration correspondante
fait echouer le demarrage, au lieu de laisser la base diverger jusqu'a la premiere requete comme le
faisait `update`.

Pour repartir d'une base vide : `docker compose down -v && docker compose up -d`.

## Mouvements de stock

Le stock reel d'un article est la somme de ses entrees moins celle de ses sorties — jamais une
colonne « quantite en stock » tenue a jour a cote : une colonne se desynchronise au premier
traitement interrompu, une somme de mouvements non.

- Une **vente** sort la marchandise du magasin. Vente, lignes et mouvements sont ecrits dans une
  seule transaction : si une ligne manque de stock, la vente entiere est refusee. Vendre la moitie
  d'un panier sans le dire serait pire que refuser.
- Une **commande fournisseur** fait entrer la marchandise **a sa livraison**, pas a son
  enregistrement : le stock ne monte plus avant que le camion n'arrive.
- Une **commande client** ne bouge pas le stock par elle-meme : c'est un engagement. La sortie a
  lieu quand une vente la sert (`POST /commandes-clients/{id}/vente`).
- Le sens d'un mouvement vient de la route appelee (`/mouvements/entree`, `/mouvements/sortie`) et
  jamais du corps de la requete, sans quoi il suffirait de mentir sur le type pour creer du stock.
  Le **motif** obeit a la meme regle : il dit ce qui a reellement eu lieu (livraison, vente,
  annulation, correction, saisie manuelle) et non ce que l'appelant declare. Sans lui, deux
  entrees de 15 sur un article, l'une venue du fournisseur et l'autre d'une vente annulee, seraient
  indiscernables — et c'est justement ce qu'on cherche a comprendre quand un stock ne tombe pas
  juste.

## Les deux facons de vendre

**Au comptoir.** La vente se construit article par article : c'est le supermarche, ou l'on ne
connait le panier qu'une fois le dernier article passe.

```
POST  /gestiondestock/v1/ventes/create                    la vente et ses premieres lignes
POST  /gestiondestock/v1/ventes/{id}/lignes               un article de plus
PATCH /gestiondestock/v1/ventes/{id}/client/{idClient}    a qui l'on vend
```

Chaque ajout sort immediatement sa quantite du magasin, et echoue si le stock ne suit pas — sans
rien laisser derriere lui.

Le **client appartient a la vente** : il se donne a la creation, ou s'attribue apres coup tant que
la vente n'est ni annulee ni facturee — le caissier ne sait pas toujours d'avance a qui il vend,
le client se faisant souvent connaitre au moment de payer. Il reste facultatif : la vente de
comptoir anonyme est le cas ordinaire.

**Sur commande client.** La commande est un engagement : elle ne touche pas au stock. C'est la
vente qui la sert qui sort la marchandise.

```
POST /gestiondestock/v1/commandes-clients/{id}/vente
```

La commande doit etre `VALIDEE` — servir une commande encore en preparation reviendrait a sortir
une marchandise que personne n'a confirmee. La vente reprend ses lignes, le stock sort, et la
commande passe `LIVREE`, ce qui la fige : une commande ne se sert donc qu'une fois. Le tout dans
une seule transaction — servir a moitie une commande sans le dire serait pire que de refuser.

Le client de la commande devient celui de la vente : la lecture n'a ensuite qu'un seul chemin a
suivre, que la vente vienne du comptoir ou d'une commande. La facture porte le nom du client
lorsqu'il est connu, et reste anonyme sinon — c'est le ticket de caisse, pas une anomalie.

### Vendre sans reseau

Une vente faite sur un poste hors ligne s'envoie a la reconnexion. Elle est **un fait a constater,
non une transaction a autoriser** : la marchandise est deja partie.

```
POST /gestiondestock/v1/ventes/synchronisation

{ "code": "V-1042",
  "referenceClient": "3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90",
  "datevente": "2026-09-21T09:14:00Z",
  "ligneVente": [ { "article": { "id": 5 }, "quantite": 2, "prixUnitaire": 5000 } ] }
```

Trois differences avec une vente de comptoir, toutes tirees de cette seule phrase.

**Une identite venue du poste de vente.** `referenceClient` est un UUID que le poste tire
lui-meme. Rejouer la meme reference rend la vente deja enregistree au lieu d'en creer une
seconde : un telephone qui perd le reseau au milieu d'un envoi ne sait pas si l'envoi est passe,
il reessaie. L'identifiant de base ne pouvait pas servir a cela — il n'existe qu'une fois la vente
ecrite, donc trop tard. Deux envois simultanes de la meme reference sont rattrapes aussi : le
second retrouve le travail du premier, ce qui est exactement ce qu'il demandait.

La reference est facultative sur `/ventes/create` et y fait le meme office : le comptoir aussi
peut perdre sa reponse.

**Sa date reelle.** `datevente` fait foi ici, et nulle part ailleurs. Une vente de 9 h
synchronisee a midi doit peser sur la caisse de 9 h, et son mouvement de stock porte la meme date.
Une date dans le futur est refusee, avec cinq minutes de tolerance — l'horloge d'un telephone
derive, et refuser une vente pour deux minutes d'avance rendrait la synchronisation capricieuse.
Sur `/ventes/create`, la date envoyee est ignoree : antidater une vente de comptoir ferait entrer
une recette dans une caisse deja arretee.

**Le stock ne s'y oppose pas.** Deux caisses vendent hors ligne le dernier sac de ciment ; la
seconde synchronise et serait refusee, alors que le sac est parti. Refuser n'empecherait rien —
cela effacerait seulement la trace de ce qui a eu lieu. Le stock passe donc sous zero.

Ce n'est pas une erreur a masquer mais un **signal** : l'article remonte dans `/stock/alertes`
avec le statut `NEGATIF`, distinct de `RUPTURE`. Les deux n'appellent pas le meme geste — une
rupture se commande au fournisseur, un negatif se compte sur l'etagere.

Le reste continue de refuser une sortie au-dela du stock : `/ventes/create`, `/ventes/{id}/lignes`
et les deux routes de `/mouvements`. Laisser un appelant quelconque antidater une sortie ou passer
sous zero permettrait de fabriquer un stock qui n'a jamais existe.

## TVA

Le regime de TVA se parametre **sur l'entreprise**, a son enregistrement :

```json
{ "nom": "...", "assujettieTva": true, "tauxTva": 19.25 }
```

Il n'existait auparavant que sur l'article, ou il fallait le redire a chaque creation — et rien ne
permettait de dire qu'une entreprise n'est pas assujettie. Toutes collectaient la TVA, ce qui est
faux : certaines la reversent aux impots par declaration, d'autres n'y sont pas soumises.

L'ordre des regles, a l'emission de la facture :

1. **Entreprise non assujettie** : aucune ligne ne porte de TVA, meme si l'article en fixe une.
   La facture porte `tvaApplicable: false` — sans cette mention, un total a zero ne se distingue
   pas d'un calcul qui n'a pas eu lieu, et c'est une indication qui doit figurer sur le document.
2. **Taux porte par l'article** : il l'emporte. C'est ainsi qu'un produit exonere ou a taux reduit
   reste une exception, portee la ou elle a un sens.
3. **A defaut, le taux de l'entreprise.**

Le taux n'est donc plus exige a la creation d'un article. Une entreprise enregistree sans precision
est assujettie a **19,25 %**, le taux en vigueur au Cameroun.

L'entreprise d'une vente vient du compte connecte (voir le cloisonnement ci-dessous). Une vente qui
n'en a aucune — cas d'un compte non rattache — facture sur le seul taux de l'article.

## Corriger une vente

Une vente a deja sorti sa marchandise : contrairement a une commande, on ne peut pas reecrire une
ligne et s'en tenir la. Chaque correction ecrit un mouvement de compensation.

```
GET    /gestiondestock/v1/ventes/{id}/lignes
POST   /gestiondestock/v1/ventes/{id}/annulation
PATCH  /gestiondestock/v1/ventes/{id}/lignes/{idLigne}?quantite=5
DELETE /gestiondestock/v1/ventes/{id}/lignes/{idLigne}
```

Augmenter une quantite sort le complement, et **echoue si le magasin ne l'a pas** — c'est le
comportement voulu ; diminuer remet la difference. L'annulation rend toute la marchandise et
marque la vente **sans l'effacer** : une recette encaissee puis rendue doit pouvoir se retrouver.
Une vente annulee ne se corrige plus, sous peine de rendre deux fois.

```
GET  /gestiondestock/v1/mouvements/stockreel/{idArticle}
GET  /gestiondestock/v1/mouvements/article/{idArticle}
POST /gestiondestock/v1/mouvements/entree
POST /gestiondestock/v1/mouvements/sortie
```

## Cycle de vie des commandes

```
EN_PREPARATION ──> VALIDEE ──> PARTIELLEMENT_LIVREE ──> LIVREE
       │              │    └──────────────────────────────┘
       │              │                 └─────────────> CLOTUREE
       └──────────────┴──> ANNULEE
```

```
PATCH /gestiondestock/v1/commandes-fournisseurs/{id}/etat/{etat}
PATCH /gestiondestock/v1/commandes-clients/{id}/etat/{etat}
```

Tant que la commande n'est pas figee, ses lignes se corrigent :

```
GET    /gestiondestock/v1/commandes-fournisseurs/{id}/lignes
POST   /gestiondestock/v1/commandes-fournisseurs/{id}/lignes
PATCH  /gestiondestock/v1/commandes-fournisseurs/{id}/lignes/{idLigne}?quantite=25
DELETE /gestiondestock/v1/commandes-fournisseurs/{id}/lignes/{idLigne}
```

Ces operations n'ecrivent aucun mouvement de stock et n'ont rien a rattraper : la marchandise
n'entre qu'a la livraison, qui relit les lignes telles qu'elles sont a ce moment-la. Les memes
routes existent sous `/commandes-clients`.

### Livraison partielle

Une commande se soldait d'un coup : recevoir 6 unites sur 10 obligeait a mentir en declarant tout
livre, ou a ne rien enregistrer en attendant le reste — les deux faussent le stock, et le second
plus longtemps.

```
POST /gestiondestock/v1/commandes-fournisseurs/{id}/receptions
POST /gestiondestock/v1/commandes-clients/{id}/vente-partielle

[ { "idLigne": 12, "quantite": 6 } ]
```

La quantite est celle de **cette arrivee**, pas le cumul : c'est ce qui a ete compte au
dechargement. Chaque ligne porte `quantiteLivree` et ce qui reste — `resteALivrer` cote
fournisseur, `resteAServir` cote client — un compteur global sur la commande ne dirait pas quel
article manque.

**L'etat n'est pas declare, il est constate** : tout est arrive, la commande passe `LIVREE` ; il
manque quelque chose, elle passe `PARTIELLEMENT_LIVREE` et se sert a nouveau quand le reste
arrive. Recevoir au-dela de ce qui reste attendu est refuse : ce n'est pas une livraison, c'est
une erreur de comptage ou une commande a corriger.

Declarer une commande `LIVREE` revient a recevoir tout le reliquat — le raccourci passe par la
meme operation, pour qu'un seul chemin ecrive le stock. Une commande partiellement livree ne se
corrige plus et ne s'annule plus : du stock est deja entre.

Une commande client peut donc etre servie par **plusieurs ventes**. L'index unique qui l'interdisait
est tombe avec ce lot ; ce qu'il protegeait — sortir deux fois la meme marchandise — est desormais
garanti par le reliquat.

Une commande nait `EN_PREPARATION`. `LIVREE`, `CLOTUREE` et `ANNULEE` sont **definitifs** : une
commande livree ne se deprogramme pas — la marchandise a bouge, et l'annuler laisserait le stock
mentir — et une commande annulee ne se reprend pas, on en saisit une nouvelle. C'est aussi ce qui
garantit qu'une commande fournisseur n'entre en stock qu'une fois : une seconde livraison est
refusee avant d'avoir relu la moindre ligne.

### Cloture d'un reliquat

Une commande partiellement livree dont le reste n'arrivera jamais restait bloquee dans cet etat :
elle figurait indefiniment parmi les commandes en cours, et le reliquat continuait de paraitre
attendu.

```
POST /gestiondestock/v1/commandes-fournisseurs/{id}/cloture
POST /gestiondestock/v1/commandes-clients/{id}/cloture

{ "motif": "Fournisseur en rupture, article arrete" }
```

**`CLOTUREE` n'est pas un raccourci vers `LIVREE`** : un fournisseur qui a tout livre et un qui a
fait defaut ne doivent pas se ressembler six mois plus tard. L'etat dit « on n'attend plus rien »,
pas « tout est arrive ».

Ce qui en decoule :

- **Aucun mouvement de stock.** Ce qui est arrive a ete enregistre a sa reception, ce qui manque
  n'est jamais venu. Cote client, ce qui n'a pas ete servi n'a jamais quitte le magasin : il n'y
  a rien a rendre non plus.
- **Les lignes restent intactes.** `quantiteLivree` reste en dessous de `quantite`, et l'ecart dit
  exactement ce qui n'a pas ete honore. Les raboter effacerait la seule trace du manquement. Les
  lignes de commande client remontent desormais `quantiteLivree` et `resteAServir`, que leur DTO
  ne rendait pas : le reliquat d'une commande client etait invisible de l'exterieur.
- **Le motif est obligatoire.** « On a clos » sans dire pourquoi ne sert a rien a celui qui relira
  l'historique ; `motifCloture` remonte avec la commande.
- **Seule une commande `PARTIELLEMENT_LIVREE` se clot.** Une commande dont rien n'est arrive
  s'annule — la clore laisserait croire qu'une partie est passee.
- **La route des etats ne clot pas.** `PATCH .../etat/CLOTUREE` est refuse : la cloture y
  arriverait sans motif.

Renoncer a un reliquat est reserve aux roles `ADMIN` et `MANAGER`. Le magasinier enregistre ce
qui arrive ; cesser d'attendre un fournisseur, ou de devoir a un client, est une decision.

## Facturation

Rien ne calculait de montant dans cette application : une vente portait des lignes, chacune une
quantite et un prix, et personne n'en faisait jamais la somme. Une facture est ce calcul, **fige**
au moment ou on l'emet.

```
POST /gestiondestock/v1/ventes/{id}/facture        emission
GET  /gestiondestock/v1/ventes/{id}/facture
GET  /gestiondestock/v1/factures/{id}
GET  /gestiondestock/v1/factures/numero/{numero}
GET  /gestiondestock/v1/factures?page=0&size=20&sort=dateEmission,desc
POST /gestiondestock/v1/factures/{id}/annulation
```

Ce que « fige » veut dire, et pourquoi :

- Les lignes **recopient** le code, la designation, le prix et le taux de TVA. Elles ne pointent
  pas vers l'article. Un article renomme, repricé ou supprime ne doit pas changer une facture deja
  remise au client — un test verifie qu'une facture ne bouge plus quand le prix de l'article
  change.
- Le prix retenu est celui de la **ligne de vente**, pas le prix courant de l'article : c'est
  celui auquel on a vendu.
- Le total somme les montants **deja arrondis** des lignes plutot que de repartir des quantites :
  c'est la seule facon que le total corresponde a l'addition de ce que le client a sous les yeux.
  Deux decimales, arrondi commercial, decides en un seul endroit.
- Le numero vient d'une **sequence de la base** (`FA-2026-000012`). Un compteur calcule en Java —
  « le plus grand numero plus un » — donnerait le meme numero a deux factures emises en meme
  temps.

Une vente ne se facture qu'une fois (contrainte d'unicite en base, pas un controle en Java), et
pas si elle est annulee. **Une vente facturee ne se corrige plus** : changer la vente sous sa
facture la ferait mentir. Annuler la facture rouvre la vente ; la facture, elle, reste lisible —
un numero emis puis disparu est exactement ce qu'une comptabilite ne doit pas montrer.

## Cloisonnement par entreprise

`id_entreprise` existait depuis le premier jour sur presque toutes les tables **sans que rien ne le
renseigne ni ne filtre dessus** : deux entreprises partageant cette base voyaient les articles, les
clients et les ventes l'une de l'autre.

- **L'entreprise vient du compte connecte, jamais de la requete.** Un `idEntreprise` envoye dans le
  corps est ignore : ce serait une invitation a ecrire chez le voisin.
- Les listes ne rendent que l'entreprise de l'appelant, et les recherches par code sont cloisonnees
  **des la requete** — deux entreprises peuvent employer le meme code d'article, et rien ne
  l'interdit.
- Lire, modifier ou supprimer la donnee d'une autre entreprise rend un **404**, pas un 403 :
  repondre « interdit » confirmerait son existence, et permettrait de deviner ce que le voisin
  possede en essayant des identifiants.
- `ROLE_SUPER_ADMIN` regarde au-dela d'une entreprise : c'est l'editeur, celui qui les cree, pas le
  gerant. `ROLE_ADMIN` administre la sienne.

Tout cela est decide en un seul endroit, `config/security/Cloisonnement`, pour que la reponse soit
la meme partout.

Trois cas donnent « pas d'entreprise », et ils ne veulent pas dire la meme chose : le
super-administrateur, qui voit tout ; un compte sans entreprise, qui ne voit que les donnees qui
n'en ont pas ; et un appel hors authentification — traitement interne ou test — ou il n'y a
personne a qui demander, et ou rien n'est filtre. Toutes les routes HTTP exigeant un compte, ce
dernier cas ne se presente pas a travers l'API.

## Ouvrir une entreprise

```
POST /gestiondestock/v1/entreprises/inscription
```

```json
{
  "entreprise":      { "nom": "...", "registreCommerce": "...", "email": "...", "tel": "..." },
  "administrateur":  { "username": "...", "email": "...", "motdepasse": "...", "nom": "...", "prenoms": "..." }
}
```

L'entreprise et le compte qui l'administrera se creent **ensemble**, dans la meme transaction : un
identifiant deja pris annule l'entreprise plutot que de la laisser derriere, orpheline. Avant, il
fallait creer l'entreprise, creer un compte, puis les rattacher — et cette derniere etape n'etait
possible qu'en modifiant la base.

L'administrateur nait rattache, actif, avec `ROLE_ADMIN` et son mot de passe chiffre.

Qui peut le faire : le **super-administrateur**, ou n'importe qui sur une installation qui ne
compte encore aucune entreprise — il faut bien creer la premiere, et personne ne peut alors
l'autoriser. C'est la meme regle que pour le tout premier compte.

### Amorcage d'une installation

Le **premier compte** cree par `/api/auth/signup` sur une base vierge recoit `ROLE_SUPER_ADMIN` :
celui qui installe l'application est l'editeur, et sans ce rang personne ne pourrait ouvrir
d'entreprise. Sur une installation deja en service, la migration `V12` promeut le compte le plus
ancien **non rattache a une entreprise** — celui qui a installe, et non l'employe d'un client. Si
tous les comptes appartiennent deja a une entreprise, elle ne promeut personne : mieux vaut cela
que de promouvoir le mauvais.

## Administration des comptes

```
GET    /gestiondestock/v1/users/all
GET    /gestiondestock/v1/users/{id}
POST   /gestiondestock/v1/users/create
PATCH  /gestiondestock/v1/users/{id}/roles                    remplace les roles
PATCH  /gestiondestock/v1/users/{id}/actif/{actif}            ouvre ou ferme l'acces
PATCH  /gestiondestock/v1/users/{id}/motdepasse               reinitialisation
PATCH  /gestiondestock/v1/users/{id}/entreprise/{idEntreprise} rattachement (SUPER_ADMIN)
PATCH  /gestiondestock/v1/users/moi/motdepasse                son propre mot de passe
```

- Un compte cree **rejoint l'entreprise de son createur**, y compris par `/api/auth/signup`. Avant,
  tout compte naissait sans entreprise et voyait donc les donnees qui n'en ont pas, au lieu de
  celles de la maison qui l'embauche.
- Le mot de passe est **chiffre au passage** et n'est jamais rendu : `fromEntity` recopiait
  l'empreinte BCrypt dans le DTO, et la liste des comptes la livrait pour chacun.
- Un compte **se ferme, il ne se supprime pas** : l'employe parti reste l'auteur des ventes qu'il a
  saisies. Un compte ferme ne se connecte plus — `isEnabled` renvoyait `true` en dur.
- On ne ferme pas son propre acces, sans quoi une entreprise se retrouverait sans personne pour
  rouvrir.
- Les roles se **remplacent** : la liste envoyee est l'etat vise, ce qui permet d'en retirer un.
  Seul le super-administrateur accorde son propre rang — un administrateur qui se l'attribuerait
  sortirait de son entreprise par la porte de derriere.
- Changer son mot de passe exige l'ancien ; le reinitialiser ne le demande pas, et c'est un geste
  d'administrateur. Huit caracteres au minimum.

## Reglement des factures

```
POST   /gestiondestock/v1/factures/{id}/reglements
GET    /gestiondestock/v1/factures/{id}/reglements
DELETE /gestiondestock/v1/factures/{id}/reglements/{idReglement}
```

```json
{ "montant": 5000, "mode": "MOBILE_MONEY", "reference": "MP260921.1432.A12345" }
```

Une facture etait emise ou annulee, jamais reglee : rien ne disait ce qui avait ete encaisse, ni ce
qui restait du.

- **Les paiements partiels sont la regle** : un acompte a la commande, le solde a la livraison.
  Chaque facture porte `montantRegle`, `resteAPayer` et un statut — `IMPAYEE`,
  `PARTIELLEMENT_REGLEE` ou `REGLEE` — deduits a la lecture. Rien n'est tenu a jour sur la facture :
  une colonne « deja paye » se desynchronise au premier traitement interrompu, une somme de
  mouvements non. C'est le meme choix que pour le stock.
- Le **mode** dit ou aller verifier que l'argent est bien arrive : especes en caisse, mobile money
  par son numero de transaction, cheque qui peut revenir impaye. `ESPECES`, `MOBILE_MONEY`,
  `VIREMENT`, `CHEQUE`, `AUTRE`.
- Ce qui **depasse le reste a payer est refuse** : un trop-percu est une erreur de saisie, pas une
  situation a enregistrer.
- La date est celle de l'encaissement, jamais celle que l'appelant declare — antidater un
  reglement deplacerait une recette d'un exercice a l'autre.
- Un reglement **ne se modifie pas, il se reprend** (cheque impaye, erreur de saisie), et c'est un
  geste comptable.
- **Une facture deja encaissee ne s'annule pas** sans reprendre ses reglements : sinon de l'argent
  recu resterait sans rien en face.

La liste des factures porte le reste a payer de chacune, charge en une seule requete — les
demander facture par facture ferait une requete par ligne affichee.

## Etat du stock

```
GET /gestiondestock/v1/stock/etat
GET /gestiondestock/v1/stock/inventaire?page=0&size=20
GET /gestiondestock/v1/stock/alertes
```

```json
{
  "nombreArticles": 42, "nombreEnRupture": 3, "nombreSousSeuil": 5,
  "valeurAuCout": 1250000, "nombreSansCoutConnu": 2,
  "valeurAuPrixDeVente": 1875000
}
```

Le pendant de l'etat de caisse, cote marchandise : la caisse dit ce qui est entre, celui-ci dit ce
qui dort en rayon. Le stock se lisait article par article — personne ne pouvait dire ce que valait
l'ensemble, ni quels articles s'epuisaient.

**Deux valorisations, parce qu'elles ne repondent pas a la meme question.** `valeurAuCout` est ce
que la marchandise a coute, deduit des commandes fournisseur **livrees** ; `valeurAuPrixDeVente`
est ce qu'elle rapporterait si elle se vendait. Confondre les deux fait passer une marge pour un
avoir.

- Le cout est **moyen**, pas celui du dernier achat : un dernier achat portant sur une petite
  quantite a un prix exceptionnel valoriserait tout le stock a ce prix-la.
- Un article jamais achete par une commande — approvisionne a la main — n'a **pas de cout connu**,
  et sa valeur au cout reste vide. `nombreSansCoutConnu` le dit : sans ce compte, une valorisation
  partielle passerait pour complete.
- Une commande non livree ne donne aucun cout : la marchandise n'est pas arrivee, son prix n'a
  encore rien coute.

**Seuil d'alerte** (`seuilAlerte` sur l'article, facultatif). Chaque ligne porte un statut :

| Statut | Ce qu'il veut dire | Ce qu'il appelle |
|---|---|---|
| `NEGATIF` | il est sorti plus que le magasin n'avait recu | un comptage sur l'etagere |
| `RUPTURE` | plus rien | une commande au fournisseur |
| `SOUS_SEUIL` | sous le seuil fixe | une commande au fournisseur |
| `SUFFISANT` | au-dessus du seuil | rien |
| `SANS_SEUIL` | article non surveille | rien |

`SANS_SEUIL` n'est pas un defaut : beaucoup d'articles n'ont pas a etre surveilles, et les
confondre avec ceux qui vont bien ferait croire a une surveillance qui n'existe pas.

`NEGATIF` est apparu avec la vente hors ligne : tant que toutes les sorties etaient refusees
au-dela du stock, il ne pouvait pas exister et se confondait avec `RUPTURE`. Il est vrai quel que
soit le seuil — un article non surveille peut y tomber.

`/stock/alertes` rend ces trois premiers statuts : ce qu'il faut recommander, et ce qu'il faut
compter.

## Etat de caisse

```
GET /gestiondestock/v1/caisse/etat                                 la journee en cours
GET /gestiondestock/v1/caisse/etat?debut=2026-09-01&fin=2026-09-30
GET /gestiondestock/v1/caisse/reglements?debut=...&fin=...&page=0&size=20
```

```json
{
  "debut": "2026-09-21", "fin": "2026-09-21",
  "total": 15000, "nombreReglements": 3,
  "parMode": [
    { "mode": "ESPECES",      "total": 8000, "nombre": 2 },
    { "mode": "MOBILE_MONEY", "total": 7000, "nombre": 1 }
  ]
}
```

Les encaissements existaient sans que rien ne les additionne : savoir ce qui etait entre dans la
journee demandait d'ouvrir les factures une par une.

- **Le detail par mode n'est pas une curiosite, c'est ce qui permet de verifier** : les especes se
  comptent dans le tiroir, le mobile money se confronte au releve de l'operateur, les cheques se
  comptent en nombre. Un total global, seul, ne se controle contre rien.
- Sans dates, **la journee en cours** — c'est la question du soir, quand on ferme.
- Le total est la somme des lignes affichees, et non une requete de plus : il correspond donc
  toujours a ce qu'on a sous les yeux. Une caisse vide rend `0`, pas une absence de reponse.
- Les journees sont des **jours civils locaux**, lus dans `app.fuseauHoraire` (`Africa/Douala` par
  defaut, `FUSEAU_HORAIRE` pour en changer). Le conteneur tourne en UTC : sans ce reglage, « la
  caisse du 21 » irait de 01h00 a 01h00 en heure locale, et les encaissements du soir compteraient
  pour le lendemain.
- La borne haute d'une periode est exclue a la seconde pres dans les deux lectures — le total et
  le detail — sans quoi les deux ne se recouperaient pas.

## Acces et roles

L'API est fermee : toute route inconnue du tableau ci-dessous exige au minimum un compte valide,
et une route ajoutee demain naitra fermee.

| Ce qu'on fait | Qui le peut |
|---|---|
| Consulter (GET) | tout compte connecte |
| Entrer ou sortir du stock | ADMIN, MANAGER, MAGASINIER |
| Faire avancer une commande (PATCH) | ADMIN, MANAGER, MAGASINIER |
| Vendre, enregistrer un client | ADMIN, MANAGER, CAISSIER |
| Corriger ou annuler une vente | ADMIN, MANAGER, CAISSIER |
| Emettre une facture | ADMIN, MANAGER, CAISSIER |
| Encaisser un reglement | ADMIN, MANAGER, CAISSIER, COMPTABLE |
| Consulter la caisse | ADMIN, MANAGER, CAISSIER, COMPTABLE |
| Consulter l'etat du stock | ADMIN, MANAGER, MAGASINIER, COMPTABLE |
| Reprendre un reglement | ADMIN, COMPTABLE |
| Annuler une facture | ADMIN, MANAGER, COMPTABLE |
| Creer articles, categories, commandes | ADMIN, MANAGER, MAGASINIER |
| Cloturer un reliquat | ADMIN, MANAGER |
| Supprimer | ADMIN, MANAGER |
| Comptes et entreprises | ADMIN |
| Voir au-dela de son entreprise | SUPER_ADMIN |

`/api/auth/signup` est reserve aux administrateurs, avec une seule exception : sur une base ou
aucun compte n'existe, la premiere inscription est libre — il faut bien creer le premier, et
personne ne peut alors l'autoriser.

## Tests

```bash
./mvnw test
```

231 tests. Les tests d'integration montent leur propre PostgreSQL par Testcontainers et **exigent un
demon Docker actif** ; sans lui, l'echec porte sur l'environnement et non sur le code. Ils n'ont en
revanche plus besoin d'une base installee sur la machine.

Le conteneur de test demarre une fois pour toute la campagne, dans un bloc statique plutot que par
`@Container` : JUnit arrete un `@Container` a la fin de chaque classe, quand Spring, lui, reutilise
ses contextes en cache — la classe suivante se connectait alors a une base disparue.

## Premiers appels

```bash
# Creation d'un compte
curl -X POST http://localhost:9092/api/auth/signup -H 'Content-Type: application/json' \
  -d '{"username":"gerant","email":"gerant@exemple.test","password":"MotDePasse123!","role":["admin"]}'

# Connexion : renvoie le jeton d'acces et le jeton de rafraichissement
curl -X POST http://localhost:9092/api/auth/signin -H 'Content-Type: application/json' \
  -d '{"username":"gerant","password":"MotDePasse123!"}'

# Qui suis-je : le compte porte par ce jeton, ses roles, son entreprise
curl http://localhost:9092/gestiondestock/v1/users/moi -H "Authorization: Bearer $JETON"
```

## Authentification

```
POST /api/auth/signin     identifiant + mot de passe → jeton d'acces + jeton de rafraichissement
POST /api/auth/refresh    { "refreshToken": "..." } → un nouveau couple
POST /api/auth/logout     { "refreshToken": "..." } → ce jeton cesse de valoir
GET  /gestiondestock/v1/users/moi
```

Le jeton d'acces vaut **une heure** (`JWT_EXPIRATION_MS`), le jeton de rafraichissement **trente
jours** (`JWT_REFRESH_EXPIRATION_MS`).

Pourquoi deux jetons plutot qu'un seul de 24 h, comme avant : **un JWT ne se revoque pas**. Signe,
il vaut jusqu'a son expiration, et fermer un compte ne le rappelle pas — un employe renvoye
gardait ses acces jusqu'au lendemain. Le jeton de rafraichissement, lui, vit en base. C'est lui
qui se revoque, et c'est ce qui permet de rendre le jeton d'acces court sans obliger un caissier a
se reconnecter chaque heure.

Ce qui en decoule :

- **Chaque echange remplace le jeton.** Le precedent est revoque, pas supprime.
- **Un jeton remplace qui revient ferme tout le compte.** Il ne devait jamais revenir : le client
  qui l'a echange en a recu un autre. S'il revient, une copie circule.
- **Un jeton deconnecte qui revient ne ferme rien d'autre.** C'est un onglet reste ouvert ou une
  requete differee, pas un vol. Confondre les deux couperait la caisse du comptoir parce que
  quelqu'un s'est deconnecte de son telephone. C'est pourquoi la revocation garde son motif.
- **Se deconnecter ne ferme que son appareil.** Les autres sessions du compte continuent.
- **Fermer un acces, reinitialiser ou changer un mot de passe ferme toutes les sessions.**

`GET /users/moi` manquait, et c'est le front qui le payait : au rechargement d'une page, il a un
jeton mais aucun moyen de redemander a qui il appartient. Il devait croire son stockage local, et
gardait donc le menu d'un role retire jusqu'a l'expiration du jeton.

## Notifications

Rien ne prevenait personne : un article tombait en rupture et on l'apprenait au comptoir, devant
le client ; une facture s'emettait sans que le client le sache.

```
GET   /gestiondestock/v1/notifications?nonLues=true&page=0&size=20
GET   /gestiondestock/v1/notifications/non-lues        le nombre, pour la cloche
PATCH /gestiondestock/v1/notifications/{id}/lue
PATCH /gestiondestock/v1/notifications/lues            tout marquer lu
```

Ces routes parlent du compte connecte, et de lui seul : aucun identifiant d'utilisateur dans les
chemins. Lire les notifications d'un autre n'est pas interdit, c'est impossible.

### La regle qui tient tout

**Un echec d'envoi ne fait jamais echouer une operation metier.** Une vente ne se refuse pas
parce que le serveur SMTP est tombe.

D'ou la conception : l'operation enregistre l'intention de notifier **dans sa propre
transaction** — ce qui ne peut pas rater, c'est une insertion — et la livraison vient apres, par
un autre chemin. C'est le « mouvement plutot que solde » applique aux envois.

Deux tables, parce que ce sont deux choses :

| | `notification` | `envoi` |
|---|---|---|
| Destinataire | un compte | une adresse |
| Etat | lu / non lu | a envoyer, envoye, abandonne |
| Pourquoi separees | le client qui recoit sa facture n'a pas de compte ; le magasinier prevenu d'une rupture n'a pas d'adresse a servir |

### Ce qui declenche quoi

| Evenement | Qui est prevenu | Par ou |
|---|---|---|
| Article sous son seuil, a zero, ou sous zero | ADMIN, MANAGER, MAGASINIER de l'entreprise | in-app |
| Facture emise | le client, s'il a une adresse | courriel |
| Compte ouvert | son titulaire | courriel |

L'alerte de stock ne se declenche que sur une **sortie** : une entree ne fait jamais baisser le
stock, et verifier apres chaque reception couterait une requete pour rien. Les trois situations
portent trois messages distincts — un stock negatif se compte sur l'etagere, une rupture et un
sous-seuil se commandent au fournisseur.

**Une alerte ne se repete pas tant qu'elle n'est pas lue.** Un article sous son seuil le reste a
chaque vente : sans cette cle de regroupement, une journee de comptoir enterrerait la boite aux
lettres sous le meme message. Une fois lue, elle peut revenir — la situation qui persiste merite
d'etre rappelee.

Une notification est ecrite **par destinataire**, parce que l'etat « lu » est personnel : une
alerte que le magasinier a traitee ne doit pas disparaitre de l'ecran du gerant.

### La file des envois

Un expediteur la vide toutes les minutes (`NOTIFICATIONS_INTERVALLE_MS`).

- **Chaque envoi dans sa propre transaction.** Sans cela, le premier courriel refuse emporterait
  dans son rollback le compte-rendu de tous les autres du paquet : les reussis repartiraient au
  passage suivant, et le client recevrait sa facture plusieurs fois.
- **L'attente double a chaque echec**, d'une minute a une demi-heure. Une adresse momentanement
  injoignable retentee toutes les minutes remplit les journaux sans rien arranger.
- **Apres six tentatives, l'envoi est abandonne — pas efface.** Une facture qui n'est jamais
  partie est une question qu'on se posera, et supprimer la ligne supprimerait la reponse.

```
EMAIL_HOST=smtp.gmail.com
EMAIL_PORT=587
EMAIL_USERNAME=adresse@exemple.test
EMAIL_PASSWORD=<mot de passe d'application>
EMAIL_FROM=adresse@exemple.test
```

**Tant que `EMAIL_HOST` est vide, rien ne part et rien n'echoue** : les courriels s'accumulent
dans la file et s'enverront tels quels le jour ou les variables arrivent. Ne pas les abandonner
est volontaire — une file qui se vide dans le neant serait pire qu'une file qui attend.

Avec Gmail, `EMAIL_PASSWORD` n'est pas le mot de passe du compte mais un **mot de passe
d'application**, a generer dans les parametres de securite Google. Il ouvre l'envoi de courriels
au nom de l'adresse : c'est un secret a part entiere, et il n'a rien a faire ailleurs que dans
`.env`.

### Ce qui n'y est pas encore

**Web Push.** Notifier le navigateur d'une application qui n'existe pas encore ne mene nulle
part : cela demande des cles VAPID, une table d'abonnements et un service worker cote client. Le
canal viendra avec le front, qui interrogera d'ici la `/notifications/non-lues`.

## CORS

L'API n'etait joignable depuis un navigateur que sur sa route de connexion — un
`@CrossOrigin(origins="*")` isole sur `AuthControler`. Le front obtenait son jeton, puis le
navigateur lui refusait tout le reste, sans que rien cote serveur ne le signale.

```
CORS_ORIGINES=http://localhost:4200,https://gestion.exemple.test
```

Des origines nommees, jamais `*` : l'etoile ouvrait l'API a n'importe quelle page du web. Les
identifiants ne sont pas autorises — l'API ne s'appuie sur aucun cookie, le jeton voyage dans
l'en-tete `Authorization`.

## Deploiement

Le serveur heberge deja plusieurs applications ; celle-ci n'en partage aucune ressource : reseau
Docker, PostgreSQL, volume et port lui sont propres (`docker-compose.prod.yml`).

Le serveur n'a ni JDK ni Maven — le Dockerfile en deux temps les apporte le temps de la
construction, et l'image finale n'embarque qu'un JRE.

```bash
# Depuis le poste de developpement : envoi de l'arbre commite
git archive --format=tar HEAD | ssh jumpy@<serveur> \
  'rm -rf ~/apps/gestionstock/source.new && mkdir -p ~/apps/gestionstock/source.new \
   && tar -x -C ~/apps/gestionstock/source.new \
   && cd ~/apps/gestionstock && rm -rf source.old && mv source source.old && mv source.new source'

# Sur le serveur : construction et demarrage
ssh jumpy@<serveur> 'cd ~/apps/gestionstock/source && docker build -t gestionstock:latest .'
ssh jumpy@<serveur> 'cd ~/apps/gestionstock && docker compose -p gestionstock -f docker-compose.prod.yml up -d'
```

Le `.env` de production vit dans `~/apps/gestionstock/.env`, en `chmod 600`, et ses secrets sont
generes sur le serveur (`openssl rand`) : ils n'ont jamais a transiter par un poste de travail.

Sonde : `curl http://<serveur>:9092/gestiondestock/v1/articles/all` — un **401** signifie que
l'application tourne et que la securite fait son office.

`APP_BIND` vaut `0.0.0.0` pour que l'API soit joignable depuis le LAN pendant le developpement ;
la passer a `127.0.0.1` la referme sur le serveur seul.

## Listes et recherches

Les listes existent en deux formes. `/all` rend tout d'un bloc — passable sur quelques dizaines de
lignes — et la route sans suffixe rend une tranche :

```
GET /gestiondestock/v1/articles?page=0&size=20&sort=codeArticle,asc
GET /gestiondestock/v1/clients?page=0&size=20
GET /gestiondestock/v1/fournisseur?page=0&size=20
GET /gestiondestock/v1/ventes?page=0&size=20
```

Les recherches par code, nom ou courriel ont leur propre segment (`/articles/code/{code}`,
`/fournisseur/nom/{nom}`, `/users/email/{email}`, `/ventes/code/{code}`) : elles partageaient le
motif de la recherche par identifiant, `/articles/{id}` et `/articles/{code}` etant le meme chemin
pour Spring, et n'etaient donc pas joignables.

### Filtrer

Les listes etaient paginees mais pas filtrables : `/articles?page=3` rendait la page 3 de tout le
catalogue. Un caissier qui tape « cim » pour trouver « Sac de ciment » n'avait rien, et sur un
telephone c'est la difference entre utilisable et inutilisable.

```
GET /gestiondestock/v1/articles?q=cim&idCategory=3
GET /gestiondestock/v1/clients?q=690112233
GET /gestiondestock/v1/fournisseur?q=cimenterie
```

| Liste | Ce que `q` cherche |
|---|---|
| `/articles` | code, designation — plus `idCategory` en filtre separe |
| `/clients` | nom, prenoms, courriel, numero de telephone |
| `/fournisseur` | nom, prenom, courriel, numero de telephone |

La casse et les espaces de bord ne comptent pas : le clavier d'un telephone met une majuscule au
premier mot et un espace apres chaque mot. `q` absent ou vide ne filtre rien — une seule route
sert a lister et a chercher.

Detail d'implementation qui merite d'etre su : `q` vaut la chaine vide, jamais `null`, quand il ne
filtre pas. Un parametre nul arrive en base sans type, et PostgreSQL, voyant `lower($1)`, doit
choisir entre `lower(text)` et `lower(bytea)` — il prend le second, et la requete echoue sur
« function lower(bytea) does not exist ».

## Limites connues

A savoir avant de reprendre le developpement :

- Les mouvements anterieurs a la V5 n'ont pas de motif, et aucun ne leur a ete invente.
- Les donnees anterieures au cloisonnement n'ont pas d'entreprise. Elles restent visibles des
  comptes qui n'en ont pas eux-memes, et du super-administrateur ; une reprise les rattacherait.
- Le taux applique est fige a l'emission de la facture, mais deux ventes du meme article au meme
  moment ne peuvent pas avoir deux taux : l'exception se porte sur l'article, pas sur la ligne.
- Une commande cloturee ne se rouvre pas. Si le fournisseur livre finalement, il faut saisir une
  nouvelle commande — ce qui est voulu, mais demande de la ressaisie.
- Web Push n'est pas branche : les notifications se lisent en interrogeant l'API. Le canal viendra
  avec le front.
- Rien ne rapproche les encaissements d'un relevé : le mode et la reference sont saisis, personne
  ne les confronte a ce que la banque ou l'operateur mobile a reellement recu.
- Un retour de marchandise ne se constate pas : une commande livree etant definitive, il faudra
  une operation dediee plutot qu'un retour en arriere.
- **Spring Boot 4 est disponible et n'est pas pris.** Il repose sur Spring Framework 7, deplace des
  modules et retire les API depreciees de toute la ligne 3.x : c'est une migration en soi, a mener
  une fois celle-ci eprouvee. springdoc devra alors passer en 3.x, sa ligne 2.x etant alignee sur
  Boot 3.
- Un avertissement au demarrage : `InitializeUserDetailsBeanManagerConfigurer` signale que le
  `DaoAuthenticationProvider` declare rend inutile la configuration automatique. C'est notre cas
  et c'est voulu — reste a le taire, ce qui tient en une ligne de configuration.
