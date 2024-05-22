package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeFourDto;

import java.util.List;

public interface CommandeFourService {
    CommandeFourDto save( CommandeFourDto dto);
    CommandeFourDto findById(Long id);
    CommandeFourDto findByCode(String code);
    List< CommandeFourDto> findAll();
    void delete(Long id);
}
