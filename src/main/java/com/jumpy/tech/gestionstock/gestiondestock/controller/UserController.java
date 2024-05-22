package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.UserControllerApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.UserDto;
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
    public ResponseEntity<UserDto> delete(Long idUser) {
        userService.delete(idUser);
        return ResponseEntity.ok().build();
    }
}
