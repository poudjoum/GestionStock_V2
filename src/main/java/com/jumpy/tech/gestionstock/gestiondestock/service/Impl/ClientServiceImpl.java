package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.dto.ClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Client;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ClientRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.ClientService;
import com.jumpy.tech.gestionstock.gestiondestock.validator.ClientValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ClientServiceImpl implements ClientService {
     private ClientRepository clientRepository;

    public ClientServiceImpl(ClientRepository clientRepository){
        this.clientRepository=clientRepository;
    }
    @Override
    @Transactional
    public ClientDto save(ClientDto dto) {
        List<String>errors= ClientValidator.validate(dto);
        if(!errors.isEmpty()){
            log.error("Client not Valid {}",dto);
            throw new InvalidEntityException("Le client n'est pas valide", ErrorCodes.CLIENT_NOT_VALID,errors);
        }
        Client savedClient=clientRepository.save(ClientDto.toEntity(dto));
        return ClientDto.fromEntity(savedClient);
    }

    @Override
    public ClientDto findById(Long id) {
        if(id==null){
            log.error("Client ID is null");
            return null;
        }
        // `client.get()` levait NoSuchElementException — un 500 — avant que le orElseThrow, pose
        // sur un Optional toujours plein, n'ait la moindre chance de rendre le 404 annonce.
        return clientRepository.findById(id)
                .map(ClientDto::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun client avec l'identifiant " + id + " n'a été trouvé",
                        ErrorCodes.CLIENT_NOT_FOUND));
    }

    @Override
    public List<ClientDto> findAll() {
        return clientRepository.findAll().stream()
                .map(ClientDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public Page<ClientDto> findAll(Pageable pageable) {
        return clientRepository.findAll(pageable).map(ClientDto::fromEntity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if(id==null){
            log.error("Client Id est null");
            return;
        }
        clientRepository.deleteById(id);
    }
}
