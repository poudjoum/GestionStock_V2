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
- Une **commande fournisseur** fait entrer la marchandise. Le modele n'ayant pas d'etat de
  commande, l'enregistrement vaut reception ; le jour ou la commande aura un cycle de vie, l'entree
  devra se faire au passage en « livree ».
- Une **commande client** ne bouge pas le stock : c'est un engagement, pas une sortie.
- Le sens d'un mouvement vient de la route appelee (`/mouvements/entree`, `/mouvements/sortie`) et
  jamais du corps de la requete, sans quoi il suffirait de mentir sur le type pour creer du stock.

```
GET  /gestiondestock/v1/mouvements/stockreel/{idArticle}
GET  /gestiondestock/v1/mouvements/article/{idArticle}
POST /gestiondestock/v1/mouvements/entree
POST /gestiondestock/v1/mouvements/sortie
```

## Acces et roles

L'API est fermee : toute route inconnue du tableau ci-dessous exige au minimum un compte valide,
et une route ajoutee demain naitra fermee.

| Ce qu'on fait | Qui le peut |
|---|---|
| Consulter (GET) | tout compte connecte |
| Entrer ou sortir du stock | ADMIN, MANAGER, MAGASINIER |
| Vendre, enregistrer un client | ADMIN, MANAGER, CAISSIER |
| Creer articles, categories, commandes | ADMIN, MANAGER, MAGASINIER |
| Supprimer | ADMIN, MANAGER |
| Comptes et entreprises | ADMIN |

`/api/auth/signup` est reserve aux administrateurs, avec une seule exception : sur une base ou
aucun compte n'existe, la premiere inscription est libre — il faut bien creer le premier, et
personne ne peut alors l'autoriser.

## Tests

```bash
./mvnw test
```

33 tests. Les tests d'integration montent leur propre PostgreSQL par Testcontainers et **exigent un
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

- Pas de cycle de vie des commandes (commandee, livree, annulee), d'ou le choix de faire entrer la
  marchandise des l'enregistrement d'une commande fournisseur.
- Une commande ou une vente ne se modifie pas : ni ajout de ligne, ni retrait, ni correction de
  quantite.
- **Spring Boot 4 est disponible et n'est pas pris.** Il repose sur Spring Framework 7, deplace des
  modules et retire les API depreciees de toute la ligne 3.x : c'est une migration en soi, a mener
  une fois celle-ci eprouvee. springdoc devra alors passer en 3.x, sa ligne 2.x etant alignee sur
  Boot 3.
- Spring Boot 3.2.5 n'est plus suivi, et JJWT 0.11.5 emploie une API depreciee.
