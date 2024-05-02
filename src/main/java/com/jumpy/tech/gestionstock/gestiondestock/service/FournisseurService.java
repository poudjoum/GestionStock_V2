package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.FournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.UserDto;

import java.util.List;

public interface FournisseurService {
    FournisseurDto save(FournisseurDto dto);
    FournisseurDto findById(Long Id);
    FournisseurDto findFournisseurByNom(String nomFournisseur);
    List<FournisseurDto> findAll();
    void delete(Long id);
}
