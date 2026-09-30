import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MisesAJour } from './noyau/mises-a-jour';

@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  template: '<router-outlet />',
})
export class App {
  constructor() {
    // Ici, a la racine, et non dans la coque : la page du ticket d'un client, hors de la coque,
    // doit elle aussi passer a la version deployee.
    inject(MisesAJour).demarrer();
  }
}
