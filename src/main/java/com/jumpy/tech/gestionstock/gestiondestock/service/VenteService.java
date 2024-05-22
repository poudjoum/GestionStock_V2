package com.jumpy.tech.gestionstock.gestiondestock.service;


import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;

import java.util.List;

public interface VenteService {
    VenteDto save(VenteDto dto);
    VenteDto findById(Long id);
    List<VenteDto> findAll();
    VenteDto findVenteByCode(String codeVente);
    void delete(Long id);
}
