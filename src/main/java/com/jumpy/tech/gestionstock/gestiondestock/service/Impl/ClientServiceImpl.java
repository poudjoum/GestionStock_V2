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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ClientServiceImpl implements ClientService {
     private ClientRepository clientRepository;

    public ClientServiceImpl(ClientRepository clientRepository){
        this.clientRepository=clientRepository;
    }
    @Override
    public ClientDto save(ClientDto dto) {
        List<String>errors= ClientValidator.validate(dto);
        if(!errors.isEmpty()){
            log.error("Client not Valid {}",dto);
            throw new InvalidEntityException("Client is not valid", ErrorCodes.CLIENT_NOT_VALID,errors);
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
        Optional<Client> client=clientRepository.findById(id);
        ClientDto dto=ClientDto.fromEntity(client.get());

        return Optional.of(dto).orElseThrow(()->
                new EntityNotFoundException("Aucun Client avec l' ID "+id+" n'a été trouvé dans la Base de Données",ErrorCodes.CLIENT_NOT_FOUND));
    }

    @Override
    public List<ClientDto> findAll() {
        return clientRepository.findAll().stream()
                .map(ClientDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        if(id==null){
            log.error("Client Id est null");
            return;
        }
        clientRepository.deleteById(id);
    }
}
