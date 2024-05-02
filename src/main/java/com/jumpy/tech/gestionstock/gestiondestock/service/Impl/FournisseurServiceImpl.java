package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.dto.FournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Fournisseur;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.FournisseurRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.FournisseurService;
import com.jumpy.tech.gestionstock.gestiondestock.validator.FournisseurValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service

public class FournisseurServiceImpl implements FournisseurService {
    private static final Logger log = LoggerFactory.getLogger(FournisseurServiceImpl.class);
    private FournisseurRepository fournisseurRepository;
    public FournisseurServiceImpl(FournisseurRepository fournisseurRepository){
        this.fournisseurRepository=fournisseurRepository;
    }
    @Override
    public FournisseurDto save(FournisseurDto dto) {
        List<String> errors= FournisseurValidator.validate(dto);
         if(!errors.isEmpty()){
             log.error(" Fournisseur not Valid {}",dto);
             throw new InvalidEntityException("Founisseur is not Valid", ErrorCodes.FOURNISSEUR_NOT_VALID,errors);
         }
        Fournisseur four=fournisseurRepository.save(FournisseurDto.toEntity(dto));
        return FournisseurDto.fromEntity(four);
    }

    @Override
    public FournisseurDto findById(Long id) {
        if(id==null){
            log.error("Fournisseur Id is null");
        }
        Optional<Fournisseur> four=fournisseurRepository.findById(id);
        FournisseurDto dto= FournisseurDto.fromEntity(four.get());

        return Optional.of(dto).orElseThrow(()-> new EntityNotFoundException("Aucun Fournisseur avec l'id "+id+" n' a été trouvé dans la base de donnée",ErrorCodes.FOURNISSEUR_NOT_FOUND));
    }

    @Override
    public FournisseurDto findFournisseurByNom(String nomFournisseur) {
        if(!StringUtils.hasLength(nomFournisseur)){
            log.error("Le nom Fournisseur est vide ");
        }
        Optional<Fournisseur> four=fournisseurRepository.findFournisseurByNom(nomFournisseur);
        FournisseurDto dto= FournisseurDto.fromEntity(four.get());

        return Optional.of(dto).orElseThrow(()-> new EntityNotFoundException("Aucun Fournisseur avec le nom  "+nomFournisseur+" n' a été trouvé dans la base de donnée",ErrorCodes.FOURNISSEUR_NOT_FOUND));
    }

    @Override
    public List<FournisseurDto> findAll() {
      return fournisseurRepository.findAll().stream()
              .map(FournisseurDto::fromEntity)
              .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        if(id==null){
            log.error("Fournisseur Id is null");
             return;
        }fournisseurRepository.deleteById(id);

    }
}
