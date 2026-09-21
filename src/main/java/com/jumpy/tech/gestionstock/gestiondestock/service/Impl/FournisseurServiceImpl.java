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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service

public class FournisseurServiceImpl implements FournisseurService {
    private static final Logger log = LoggerFactory.getLogger(FournisseurServiceImpl.class);
    private FournisseurRepository fournisseurRepository;
    public FournisseurServiceImpl(FournisseurRepository fournisseurRepository){
        this.fournisseurRepository=fournisseurRepository;
    }
    @Override
    @Transactional
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
            throw new InvalidEntityException("Aucun Fournisseur ne peut etre cherche sans identifiant",
                    ErrorCodes.FOURNISSEUR_NOT_VALID);
        }
        return fournisseurRepository.findById(id)
                .map(FournisseurDto::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun Fournisseur avec l'id " + id + " n'a ete trouve dans la base de donnees",
                        ErrorCodes.FOURNISSEUR_NOT_FOUND));
    }

    @Override
    public FournisseurDto findFournisseurByNom(String nomFournisseur) {
        if(!StringUtils.hasLength(nomFournisseur)){
            log.error("Le nom Fournisseur est vide ");
            throw new InvalidEntityException("Aucun Fournisseur ne peut etre cherche sans nom",
                    ErrorCodes.FOURNISSEUR_NOT_VALID);
        }
        return fournisseurRepository.findFournisseurByNom(nomFournisseur)
                .map(FournisseurDto::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun Fournisseur avec le nom " + nomFournisseur + " n'a ete trouve dans la base de donnees",
                        ErrorCodes.FOURNISSEUR_NOT_FOUND));
    }

    @Override
    public List<FournisseurDto> findAll() {
      return fournisseurRepository.findAll().stream()
              .map(FournisseurDto::fromEntity)
              .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if(id==null){
            log.error("Fournisseur Id is null");
             return;
        }fournisseurRepository.deleteById(id);

    }
}
