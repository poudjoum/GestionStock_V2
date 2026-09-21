package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.UserDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;

import java.util.List;

/**
 * Administration des comptes.
 *
 * Elle etait absente : `/users` rendait tous les comptes de toutes les entreprises, un compte ne
 * pouvait etre rattache a une entreprise qu'en modifiant la base a la main, et rien ne permettait
 * ni de changer un role, ni de fermer un acces, ni de reinitialiser un mot de passe.
 */
public interface UserService {

    UserDto save(UserDto dto);

    UserDto findById(Long id);

    UserDto findUserByEmail(String email);

    /**
     * Le compte connecte, ses roles et son entreprise.
     *
     * Il manquait, et c'est le front qui le paie : au rechargement d'une page, il a un jeton mais
     * aucun moyen de redemander a qui il appartient. Il devrait croire son stockage local — donc
     * garder le menu d'un role retire jusqu'a l'expiration du jeton.
     */
    UserDto moi();

    List<UserDto> findAll();

    /**
     * Remplace les roles d'un compte.
     *
     * Remplace, et n'ajoute pas : la liste envoyee est l'etat vise, ce qui permet de retirer un
     * role sans avoir a le demander separement.
     */
    UserDto changerRoles(Long id, List<ERole> roles);

    /**
     * Ouvre ou ferme un acces.
     *
     * Fermer plutot que supprimer : l'employe parti reste l'auteur des ventes qu'il a saisies, et
     * effacer son compte rendrait cet historique illisible.
     */
    UserDto changerActivation(Long id, boolean actif);

    /**
     * Rattache un compte a une entreprise. Reserve au super-administrateur : deplacer un compte
     * d'une entreprise a l'autre, c'est donner a quelqu'un les donnees d'un tiers.
     */
    UserDto rattacherAEntreprise(Long id, Long idEntreprise);

    /** Reinitialise le mot de passe d'un compte, sans connaitre l'ancien. Geste d'administrateur. */
    UserDto reinitialiserMotDePasse(Long id, String nouveauMotDePasse);

    /**
     * Change son propre mot de passe.
     *
     * L'ancien est exige : sans lui, un poste laisse ouvert une minute suffirait a verrouiller le
     * compte de son titulaire.
     */
    UserDto changerSonMotDePasse(String ancien, String nouveau);

    void delete(Long id);
}
