package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
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
     private final Cloisonnement cloisonnement;

    public ClientServiceImpl(ClientRepository clientRepository, Cloisonnement cloisonnement){
        this.clientRepository=clientRepository;
        this.cloisonnement=cloisonnement;
    }
    @Override
    @Transactional
    public ClientDto save(ClientDto dto) {
        List<String>errors= ClientValidator.validate(dto);
        if(!errors.isEmpty()){
            log.error("Client not Valid {}",dto);
            throw new InvalidEntityException("Le client n'est pas valide", ErrorCodes.CLIENT_NOT_VALID,errors);
        }
        Client client = ClientDto.toEntity(dto);
        if (client.getId() != null) {
            client.setIdEntreprise(client(client.getId()).getIdEntreprise());
        } else if (cloisonnement.filtre()) {
            client.setIdEntreprise(cloisonnement.entrepriseCourante());
        }
        return ClientDto.fromEntity(clientRepository.save(client));
    }

    private Client client(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun client avec l'identifiant " + id + " n'a été trouvé",
                        ErrorCodes.CLIENT_NOT_FOUND));
        cloisonnement.verifierAcces(client.getIdEntreprise(), "client", id);
        return client;
    }

    @Override
    public ClientDto findById(Long id) {
        if(id==null){
            log.error("Client ID is null");
            return null;
        }
        // `client.get()` levait NoSuchElementException — un 500 — avant que le orElseThrow, pose
        // sur un Optional toujours plein, n'ait la moindre chance de rendre le 404 annonce.
        return ClientDto.fromEntity(client(id));
    }

    @Override
    public List<ClientDto> findAll() {
        return (cloisonnement.filtre()
                ? clientRepository.findAllByIdEntreprise(cloisonnement.entrepriseCourante())
                : clientRepository.findAll()).stream()
                .map(ClientDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public Page<ClientDto> findAll(Pageable pageable) {
        return (cloisonnement.filtre()
                ? clientRepository.findAllByIdEntreprise(cloisonnement.entrepriseCourante(), pageable)
                : clientRepository.findAll(pageable))
                .map(ClientDto::fromEntity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if(id==null){
            log.error("Client Id est null");
            return;
        }
        clientRepository.delete(client(id));
    }
}
