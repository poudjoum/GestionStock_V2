import { ApplicationConfig, isDevMode, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideServiceWorker } from '@angular/service-worker';
import { routes } from './app.routes';
import { intercepteurDelai } from './noyau/intercepteur-delai';
import { intercepteurJeton } from './noyau/intercepteur-jeton';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
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
