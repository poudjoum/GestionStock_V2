/**
 * Ou joindre l'API.
 *
 * Vide en developpement : le serveur de developpement d'Angular redirige `/api` et
 * `/gestiondestock` vers le backend (voir `proxy.conf.json`). Le front parle donc a sa propre
 * origine, ce qui evite d'avoir a gerer le CORS en local.
 */
export const environnement = {
  production: false,
  api: '',
};
