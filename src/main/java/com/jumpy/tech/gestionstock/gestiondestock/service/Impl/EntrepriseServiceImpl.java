package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Entreprise;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EntrepriseRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.EntrepriseService;
import com.jumpy.tech.gestionstock.gestiondestock.validator.EntrepriseValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EntrepriseServiceImpl implements EntrepriseService {

    private EntrepriseRepository entrepriseRepository;

    public EntrepriseServiceImpl(EntrepriseRepository entrepriseRepository){
        this.entrepriseRepository=entrepriseRepository;
    }
    @Override
    @Transactional
    public EntrepriseDto save(EntrepriseDto dto) {
        List<String> errors= EntrepriseValidator.validate(dto);
         if(!errors.isEmpty()){
             log.error("Entreprise Invalid {}",dto);
             throw new InvalidEntityException("Entreprise Invalid", ErrorCodes.ENTREPRISE_NOT_VALID,errors);
         }
         Entreprise savedEntreprise=entrepriseRepository.save(EntrepriseDto.toEntity(dto));


        return EntrepriseDto.fromEntity(savedEntreprise);
    }

    @Override
    public EntrepriseDto findById(Long id) {
        if(id==null){
            log.error("Entreprise id is null");
            throw new InvalidEntityException("Aucune Entreprise ne peut etre cherchee sans identifiant",
                    ErrorCodes.ENTREPRISE_NOT_VALID);
        }
        return entrepriseRepository.findById(id)
                .map(EntrepriseDto::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune Entreprise avec l'id " + id + " n'a ete trouvee dans la base de donnees",
                        ErrorCodes.ENTREPRISE_NOT_FOUND));
    }

    @Override
    public List<EntrepriseDto> findAll() {
        return entrepriseRepository.findAll().stream()
                .map(EntrepriseDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if(id==null){
            log.error("Entreprise ID is null");
            return;
        }
        entrepriseRepository.deleteById(id);

    }
}
