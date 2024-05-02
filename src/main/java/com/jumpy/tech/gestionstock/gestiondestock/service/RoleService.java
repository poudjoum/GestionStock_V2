package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.RoleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.UserDto;

import java.util.List;

public interface RoleService {
    RoleDto save(RoleDto dto);
    RoleDto findById(Long Id);
    List<RoleDto> findAll();
    void delete(Long id);
}
