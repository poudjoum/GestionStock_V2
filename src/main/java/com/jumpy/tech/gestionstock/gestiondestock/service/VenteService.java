package com.jumpy.tech.gestionstock.gestiondestock.service;


import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface VenteService {
    VenteDto save(VenteDto dto);
    VenteDto findById(Long id);
    List<VenteDto> findAll();
    Page<VenteDto> findAll(Pageable pageable);
    VenteDto findVenteByCode(String codeVente);
    void delete(Long id);
}
