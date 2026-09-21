package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.MotDePasseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.UserDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

public interface UserControllerApi {

    @Tag(name="Post",description = "Post Methods of Gestion de Stock APIs")
    @PostMapping(value = APP_ROOT+"/users/create",consumes = MediaType.APPLICATION_JSON_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)
    // @RequestBody manquait : le corps n'etait pas lie et le service recevait un utilisateur vide.
    ResponseEntity<UserDto> save(@RequestBody UserDto dto);

    /**
     * Qui suis-je : le compte connecte, ses roles et son entreprise.
     *
     * Declaree avant `/users/{idUser}`, qui sinon prendrait « moi » pour un identifiant.
     *
     * Elle manquait, et c'est le front qui le payait : au rechargement d'une page, il a un jeton
     * mais aucun moyen de redemander a qui il appartient. Il devait croire son stockage local, et
     * gardait donc le menu d'un role retire jusqu'a l'expiration du jeton.
     */
    @Tag(name="Get",description = "Get Methods of Gestion de Stock APIs")
    @GetMapping(value = APP_ROOT+"/users/moi",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<UserDto> moi();

    @GetMapping(value = APP_ROOT+"/users/{idUser}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<UserDto> findById(@PathVariable Long idUser);

    // Meme motif que findById : cette route etait inatteignable.
    @GetMapping(value = APP_ROOT+"/users/email/{email}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<UserDto> findUserByEmail(@PathVariable String email);

    // Le chemin etait « /users/ », avec une barre oblique finale et sans nom : les autres
    // controleurs exposent tous « /all ».
    @GetMapping(value = APP_ROOT+"/users/all",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<UserDto>> findAll();

    // --- Administration des comptes -------------------------------------------------------

    /** Remplace les roles : la liste envoyee est l'etat vise, pas un ajout. */
    @PatchMapping(value = APP_ROOT+"/users/{idUser}/roles",consumes = MediaType.APPLICATION_JSON_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<UserDto> changerRoles(@PathVariable Long idUser, @RequestBody List<ERole> roles);

    /** Ouvre ou ferme un acces, sans supprimer le compte ni ce qu'il a saisi. */
    @PatchMapping(value = APP_ROOT+"/users/{idUser}/actif/{actif}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<UserDto> changerActivation(@PathVariable Long idUser, @PathVariable boolean actif);

    /** Rattache un compte a une entreprise. Reserve au super-administrateur. */
    @PatchMapping(value = APP_ROOT+"/users/{idUser}/entreprise/{idEntreprise}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<UserDto> rattacherAEntreprise(@PathVariable Long idUser, @PathVariable Long idEntreprise);

    /** Reinitialise un mot de passe sans connaitre l'ancien : geste d'administrateur. */
    @PatchMapping(value = APP_ROOT+"/users/{idUser}/motdepasse",consumes = MediaType.APPLICATION_JSON_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<UserDto> reinitialiserMotDePasse(@PathVariable Long idUser,
                                                    @RequestBody MotDePasseDto motDePasse);

    /** Change son propre mot de passe, l'ancien a l'appui. */
    @PatchMapping(value = APP_ROOT+"/users/moi/motdepasse",consumes = MediaType.APPLICATION_JSON_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<UserDto> changerSonMotDePasse(@RequestBody MotDePasseDto motDePasse);

    @DeleteMapping(value = APP_ROOT+"/users/delete/{idUser}")
   ResponseEntity<UserDto>delete(@PathVariable Long idUser);
}
