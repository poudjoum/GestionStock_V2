import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Session } from './session';
import type { Role } from './roles';

/** Sans session, on ne va nulle part ailleurs que sur la page de connexion. */
export const gardeConnecte: CanActivateFn = () => {
  const session = inject(Session);
  const router = inject(Router);
  return session.connecte() ? true : router.createUrlTree(['/connexion']);
};

/** Deja connecte, la page de connexion n'a plus de sens : on repart vers son accueil. */
export const gardeDeconnecte: CanActivateFn = () => {
  const session = inject(Session);
  const router = inject(Router);
  return session.connecte() ? router.createUrlTree(['/']) : true;
};

/**
 * Reserve une route a certains roles.
 *
 * Une commodite de navigation, jamais une securite : le menu et les routes evitent a chacun de
 * tomber sur un ecran qui ne le concerne pas, mais c'est l'API qui refuse ce qu'elle doit
 * refuser. Un front ne protege rien — il tourne sur la machine de celui qu'il pretend limiter.
 */
export function gardeRoles(...autorises: Role[]): CanActivateFn {
  return () => {
    const session = inject(Session);
    const router = inject(Router);
    if (!session.connecte()) {
      return router.createUrlTree(['/connexion']);
    }
    const siens = session.roles();
    return autorises.some((role) => siens.includes(role))
      ? true
      : router.createUrlTree(['/notifications']);
  };
}
