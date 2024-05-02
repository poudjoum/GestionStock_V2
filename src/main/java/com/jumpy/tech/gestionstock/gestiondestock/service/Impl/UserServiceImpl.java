package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.dto.UserDto;
import com.jumpy.tech.gestionstock.gestiondestock.repository.UserRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;
@Service

public class UserServiceImpl implements UserService {
    private UserRepository userRepository;
    public UserServiceImpl(UserRepository userRepository){
        this.userRepository=userRepository;
    }
    @Override
    public UserDto save(UserDto dto) {

        return null;
    }

    @Override
    public UserDto findById(Long Id) {
        return null;
    }

    @Override
    public List<UserDto> findAll() {
        return List.of();
    }

    @Override
    public void delete(Long id) {

    }
}
