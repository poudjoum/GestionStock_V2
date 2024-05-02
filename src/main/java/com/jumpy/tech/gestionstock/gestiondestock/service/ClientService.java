package com.jumpy.tech.gestionstock.gestiondestock.service;



import com.jumpy.tech.gestionstock.gestiondestock.dto.ClientDto;

import java.util.List;

public interface ClientService {
    ClientDto save(ClientDto dto);
    ClientDto findById(Long Id);
    List<ClientDto> findAll();
    void delete(Long id);
}
