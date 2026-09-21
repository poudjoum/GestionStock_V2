package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.FournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.UserDto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface FournisseurService {
    FournisseurDto save(FournisseurDto dto);
    FournisseurDto findById(Long Id);
    FournisseurDto findFournisseurByNom(String nomFournisseur);
    List<FournisseurDto> findAll();
    /** Liste paginee, filtrable : `q` porte sur le nom, le prenom, le courriel et le numero. */
    Page<FournisseurDto> findAll(String q, Pageable pageable);
    void delete(Long id);
}
