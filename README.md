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
EN_PREPARATION ──> VALIDEE ──> LIVREE
       │              │
       └──────────────┴──────> ANNULEE
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

Une commande nait `EN_PREPARATION`. `LIVREE` et `ANNULEE` sont **definitifs** : une commande
livree ne se deprogramme pas — la marchandise a bouge, et l'annuler laisserait le stock mentir —
et une commande annulee ne se reprend pas, on en saisit une nouvelle. C'est aussi ce qui garantit
qu'une commande fournisseur n'entre en stock qu'une fois : une seconde livraison est refusee avant
d'avoir relu la moindre ligne.

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
| Annuler une facture | ADMIN, MANAGER, COMPTABLE |
| Creer articles, categories, commandes | ADMIN, MANAGER, MAGASINIER |
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

108 tests. Les tests d'integration montent leur propre PostgreSQL par Testcontainers et **exigent un
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

# Connexion : renvoie le jeton
curl -X POST http://localhost:9092/api/auth/signin -H 'Content-Type: application/json' \
  -d '{"username":"gerant","password":"MotDePasse123!"}'
```

## Deploiement

Le serveur heberge deja plusieurs applications ; celle-ci n'en partage aucune ressource : reseau
Docker, PostgreSQL, volume et port lui sont propres (`docker-compose.prod.yml`).

Le serveur n'a ni JDK ni Maven — le Dockerfile en deux temps les apporte le temps de la
construction, et l'image finale n'embarque qu'un JRE.

```bash
# Depuis le poste de developpement : envoi des sources
tar --exclude=.git --exclude=target --exclude=.env -czf /tmp/src.tgz .
scp /tmp/src.tgz jumpy@<serveur>:~/apps/gestionstock/
ssh jumpy@<serveur> 'cd ~/apps/gestionstock && tar -xzf src.tgz -C source && rm src.tgz'

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

## Limites connues

A savoir avant de reprendre le developpement :

- Les mouvements anterieurs a la V5 n'ont pas de motif, et aucun ne leur a ete invente.
- Les donnees anterieures au cloisonnement n'ont pas d'entreprise. Elles restent visibles des
  comptes qui n'en ont pas eux-memes, et du super-administrateur ; une reprise les rattacherait.
- Le taux applique est fige a l'emission de la facture, mais deux ventes du meme article au meme
  moment ne peuvent pas avoir deux taux : l'exception se porte sur l'article, pas sur la ligne.
- Servir une commande la solde d'un coup : pas de livraison partielle.
- Rien ne suit le **paiement** d'une facture : elle est emise ou annulee, jamais reglee.
- Un retour de marchandise ne se constate pas : une commande livree etant definitive, il faudra
  une operation dediee plutot qu'un retour en arriere.
- **Spring Boot 4 est disponible et n'est pas pris.** Il repose sur Spring Framework 7, deplace des
  modules et retire les API depreciees de toute la ligne 3.x : c'est une migration en soi, a mener
  une fois celle-ci eprouvee. springdoc devra alors passer en 3.x, sa ligne 2.x etant alignee sur
  Boot 3.
- Spring Boot 3.2.5 n'est plus suivi, et JJWT 0.11.5 emploie une API depreciee.
