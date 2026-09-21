# GestionDeStock

API REST de gestion de stock (Spring Boot 3.2, Java 17, PostgreSQL) : articles, categories, clients,
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

`spring.jpa.hibernate.ddl-auto` vaut `validate` : une entite modifiee sans migration correspondante
fait echouer le demarrage, au lieu de laisser la base diverger jusqu'a la premiere requete comme le
faisait `update`.

Pour repartir d'une base vide : `docker compose down -v && docker compose up -d`.

## Tests

```bash
./mvnw test
```

Un seul test aujourd'hui (`contextLoads`), et il exige la base demarree et `.env` charge.

## Premiers appels

```bash
# Creation d'un compte
curl -X POST http://localhost:9092/api/auth/signup -H 'Content-Type: application/json' \
  -d '{"username":"gerant","email":"gerant@exemple.test","password":"MotDePasse123!","role":["admin"]}'

# Connexion : renvoie le jeton
curl -X POST http://localhost:9092/api/auth/signin -H 'Content-Type: application/json' \
  -d '{"username":"gerant","password":"MotDePasse123!"}'
```

## Limites connues

A savoir avant de reprendre le developpement :

- **Les mouvements de stock ne sont pas implementes.** L'entite `MvtStk` et son repository existent,
  mais aucun service ne les ecrit ni ne les lit : une vente ou une commande ne modifie aucune
  quantite, et le stock disponible d'un article n'est calcule nulle part.
- **L'API n'est pas protegee.** `SecurityConfiguration` se termine par `anyRequest().permitAll()` :
  le filtre JWT est en place mais ne garde aucune route.
- **L'inscription est ouverte et laisse choisir son role**, `admin` compris
  (`AuthControler.registerUser`).
- Les services d'ecriture n'ont pas de frontiere transactionnelle : une commande et ses lignes sont
  enregistrees par des appels separes, sans transaction commune.
- Plusieurs services appellent `Optional.get()` avant leur `orElseThrow` : l'entite absente produit
  une erreur 500 au lieu du 404 metier attendu.
- Deux routes GET se disputent le meme chemin dans plusieurs controleurs
  (`/articles/{idArticle}` et `/articles/{codeArticle}`, idem pour `category`).
- Aucune contrainte d'unicite sur `utilisateur.username` et `utilisateur.email` : la verification est
  faite en Java avant insertion, ce qui laisse passer deux inscriptions simultanees.
- Aucune pagination sur les listes.
