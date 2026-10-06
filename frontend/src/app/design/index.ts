/**
 * Le design system de GestionStock : les composants communs aux ecrans.
 *
 * Ils prennent leurs couleurs dans `src/design/jetons.css` et rien d'autre : un ecran qui les
 * emploie n'a plus de couleur a ecrire. La vitrine (`/design`) les montre tous, dans leurs etats.
 */
export { EnTetePage } from './en-tete-page';
export { EtatVide } from './etat-vide';
export { Logo } from './logo';
export { Section } from './section';
export { Selecteur, type OptionSelecteur } from './selecteur';
export { Statut, type TonStatut } from './statut';
export { Tuile } from './tuile';
export { ScanCamera, cameraDisponible } from './scan-camera';
export { Courbe, type SerieCourbe } from './courbe';
export { Barres, Colonnes, type Barre, type Colonne } from './barres';
export { Tendance } from './tendance';
