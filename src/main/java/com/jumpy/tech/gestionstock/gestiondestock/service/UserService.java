package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.UserDto;

import java.util.List;

public interface UserService {

    UserDto save(UserDto dto);
    UserDto findById(Long Id);
    UserDto findUserByEmail(String email);
    List<UserDto> findAll();
    void delete(Long id);
}
