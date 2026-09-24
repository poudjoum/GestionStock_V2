import { ApplicationConfig, isDevMode, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { provideServiceWorker } from '@angular/service-worker';
import { routes } from './app.routes';
import { intercepteurDelai } from './noyau/intercepteur-delai';
import { intercepteurJeton } from './noyau/intercepteur-jeton';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    /*
     * Tous les champs en apparence « contour », et non plus au cas par cas.
     *
     * Le theme resserre les composants avec `density: -2`. A cette densite, un champ en apparence
     * « remplie » — celle par defaut — n'a plus la hauteur d'afficher son libelle, et le supprime
     * purement et simplement : il ne reste qu'un rectangle gris dont personne ne sait ce qu'il
     * attend. Le contour, lui, loge son libelle dans l'encoche et ne perd rien.
     *
     * Les ecrans existants portaient tous `appearance="outline"` a la main ; trente-trois champs
     * ecrits ensuite l'ont oublie, et sont partis en production sans un mot pour s'annoncer. Une
     * convention qu'il faut se rappeler n'est pas une convention : elle est ici, une fois.
     */
    { provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { appearance: 'outline' } },
    provideRouter(routes, withComponentInputBinding()),
    // Le delai vient en premier : il enveloppe donc toute la chaine, renouvellement du jeton
    // compris. Un rafraichissement qui resterait en vol bloquerait sinon la requete qui l'attend,
    // et c'est precisement le genre de silence qu'on cherche a supprimer.
    provideHttpClient(withInterceptors([intercepteurDelai, intercepteurJeton])),
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
  ],
};
