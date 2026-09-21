package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.UserControllerApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MotDePasseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.UserDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
@RestController
public class UserController implements UserControllerApi {

    private UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Override
    public ResponseEntity<UserDto> save(UserDto dto) {
        return ResponseEntity.status(HttpStatus.OK).body(userService.save(dto));
    }

    @Override
    public ResponseEntity<UserDto> findById(Long idUser) {
        return ResponseEntity.status(HttpStatus.OK).body(userService.findById(idUser));
    }

    @Override
    public ResponseEntity<UserDto> findUserByEmail(String email) {
        return ResponseEntity.status(HttpStatus.OK).body(userService.findUserByEmail(email));
    }

    @Override
    public ResponseEntity<List<UserDto>> findAll() {
        return ResponseEntity.status(HttpStatus.OK).body(userService.findAll());
    }

    @Override
    public ResponseEntity<UserDto> changerRoles(Long idUser, List<ERole> roles) {
        return ResponseEntity.ok(userService.changerRoles(idUser, roles));
    }

    @Override
    public ResponseEntity<UserDto> changerActivation(Long idUser, boolean actif) {
        return ResponseEntity.ok(userService.changerActivation(idUser, actif));
    }

    @Override
    public ResponseEntity<UserDto> rattacherAEntreprise(Long idUser, Long idEntreprise) {
        return ResponseEntity.ok(userService.rattacherAEntreprise(idUser, idEntreprise));
    }

    @Override
    public ResponseEntity<UserDto> reinitialiserMotDePasse(Long idUser, MotDePasseDto motDePasse) {
        return ResponseEntity.ok(userService.reinitialiserMotDePasse(idUser, motDePasse.getNouveau()));
    }

    @Override
    public ResponseEntity<UserDto> changerSonMotDePasse(MotDePasseDto motDePasse) {
        return ResponseEntity.ok(
                userService.changerSonMotDePasse(motDePasse.getAncien(), motDePasse.getNouveau()));
    }

    @Override
    public ResponseEntity<UserDto> delete(Long idUser) {
        userService.delete(idUser);
        return ResponseEntity.ok().build();
    }
}
