# Construction en deux temps, comme pour l'API : la machine qui construit n'a pas a ressembler a
# celle qui execute. Le serveur n'a pas Node — il n'a que Docker, et c'est suffisant.
FROM node:24-alpine AS build
WORKDIR /build

# Les dependances d'abord, seules : tant que package-lock.json ne change pas, Docker reutilise
# cette couche et la construction ne retelecharge pas 400 Mo de node_modules.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# L'image finale ne transporte ni Node, ni les sources, ni node_modules : seulement des fichiers
# statiques et le serveur qui les sert. Une trentaine de megaoctets au lieu de plusieurs centaines.
FROM nginx:alpine

# La configuration remplace celle par defaut, qui ne sait ni renvoyer index.html sur les routes
# Angular, ni relayer l'API.
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80
