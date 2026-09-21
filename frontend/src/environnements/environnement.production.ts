/**
 * En production, l'API est servie ailleurs que le front : son origine se nomme.
 *
 * Elle doit figurer dans `CORS_ORIGINES` cote serveur, faute de quoi le navigateur refusera
 * toutes les requetes apres la connexion.
 */
export const environnement = {
  production: true,
  api: 'http://192.168.1.100:9092',
};
