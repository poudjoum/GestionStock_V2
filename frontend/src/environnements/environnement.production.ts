/**
 * En production, l'API est servie sous la meme origine que le front.
 *
 * Le nginx qui sert l'application relaie `/api` et `/gestiondestock` vers le conteneur de l'API :
 * le navigateur ne voit donc qu'une seule origine, et la question du CORS ne se pose plus.
 *
 * C'est ce qui permet d'ouvrir l'application depuis n'importe quel appareil — un telephone sur le
 * reseau, un poste du bureau, un nom de domaine plus tard — sans avoir a declarer chacune de ces
 * adresses cote serveur, ni a decouvrir qu'on en a oublie une devant un ecran vide.
 */
export const environnement = {
  production: true,
  api: '',
};
