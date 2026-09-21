package com.jumpy.tech.gestionstock.gestiondestock.service;



import com.jumpy.tech.gestionstock.gestiondestock.dto.ClientDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ClientService {
    ClientDto save(ClientDto dto);
    ClientDto findById(Long Id);
    List<ClientDto> findAll();
    /** Liste paginee, filtrable : `q` porte sur le nom, les prenoms, le courriel et le numero. */
    Page<ClientDto> findAll(String q, Pageable pageable);
    void delete(Long id);
}
