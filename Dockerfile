# Construction en deux temps : la machine qui construit n'a pas a ressembler a celle qui execute,
# et l'image finale ne transporte ni Maven, ni le code source, ni le cache du depot.
#
# L'ancienne version enchainait deux `FROM` dans le meme fichier — le premier, un Ubuntu lancant
# `top`, ne servait a rien — et partait de `OpenJDK:17-jre-alpine`, qui n'existe pas : l'image
# n'a jamais pu etre construite.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Les dependances d'abord, seules : tant que pom.xml ne change pas, Docker reutilise cette couche
# et la construction ne retelecharge rien.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Un processus applicatif n'a aucune raison d'etre root dans son conteneur.
RUN addgroup -S app && adduser -S -G app app
USER app

COPY --from=build /build/target/*.jar application.jar

EXPOSE 9092
ENV JAVA_OPTS=""

# `exec` pour que la JVM soit le processus 1 et recoive SIGTERM : sinon `docker stop` attend dix
# secondes, puis tue l'application sans qu'elle ait ferme ses connexions.
#
# La configuration vient de l'environnement (DB_URL, DB_PASSWORD, JWT_SECRET... cf. .env.example).
# Depuis un conteneur, la base n'est pas sur `localhost` : c'est le nom du service compose, `db`,
# et le port interne 5432 — soit DB_URL=jdbc:postgresql://db:5432/gestionstock.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/application.jar"]
