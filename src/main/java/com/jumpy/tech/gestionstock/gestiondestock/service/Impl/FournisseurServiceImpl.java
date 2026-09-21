package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service

public class FournisseurServiceImpl implements FournisseurService {
    private static final Logger log = LoggerFactory.getLogger(FournisseurServiceImpl.class);
    private FournisseurRepository fournisseurRepository;
    private final Cloisonnement cloisonnement;
    public FournisseurServiceImpl(FournisseurRepository fournisseurRepository, Cloisonnement cloisonnement){
        this.fournisseurRepository=fournisseurRepository;
        this.cloisonnement=cloisonnement;
    }

    private Fournisseur fournisseur(Long id) {
        Fournisseur fournisseur = fournisseurRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun fournisseur avec l'identifiant " + id + " n'a été trouvé",
                        ErrorCodes.FOURNISSEUR_NOT_FOUND));
        cloisonnement.verifierAcces(fournisseur.getIdEntreprise(), "fournisseur", id);
        return fournisseur;
    }
    @Override
    @Transactional
    public FournisseurDto save(FournisseurDto dto) {
        List<String> errors= FournisseurValidator.validate(dto);
         if(!errors.isEmpty()){
             log.error(" Fournisseur not Valid {}",dto);
             throw new InvalidEntityException("Le fournisseur n'est pas valide", ErrorCodes.FOURNISSEUR_NOT_VALID,errors);
         }
        Fournisseur aEnregistrer = FournisseurDto.toEntity(dto);
        if (aEnregistrer.getId() != null) {
            aEnregistrer.setIdEntreprise(fournisseur(aEnregistrer.getId()).getIdEntreprise());
        } else if (cloisonnement.filtre()) {
            aEnregistrer.setIdEntreprise(cloisonnement.entrepriseCourante());
        }
        return FournisseurDto.fromEntity(fournisseurRepository.save(aEnregistrer));
    }

    @Override
    public FournisseurDto findById(Long id) {
        if(id==null){
            log.error("Fournisseur Id is null");
            throw new InvalidEntityException("Aucun fournisseur ne peut être cherché sans identifiant",
                    ErrorCodes.FOURNISSEUR_NOT_VALID);
        }
        return FournisseurDto.fromEntity(fournisseur(id));
    }

    @Override
    public FournisseurDto findFournisseurByNom(String nomFournisseur) {
        if(!StringUtils.hasLength(nomFournisseur)){
            log.error("Le nom Fournisseur est vide ");
            throw new InvalidEntityException("Aucun fournisseur ne peut être cherché sans nom",
                    ErrorCodes.FOURNISSEUR_NOT_VALID);
        }
        return (cloisonnement.filtre()
                ? fournisseurRepository.findFournisseurByNomAndIdEntreprise(nomFournisseur, cloisonnement.entrepriseCourante())
                : fournisseurRepository.findFournisseurByNom(nomFournisseur))
                .map(FournisseurDto::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun fournisseur nommé " + nomFournisseur + " n'a été trouvé",
                        ErrorCodes.FOURNISSEUR_NOT_FOUND));
    }

    @Override
    public List<FournisseurDto> findAll() {
      return (cloisonnement.filtre()
              ? fournisseurRepository.findAllByIdEntreprise(cloisonnement.entrepriseCourante())
              : fournisseurRepository.findAll()).stream()
              .map(FournisseurDto::fromEntity)
              .collect(Collectors.toList());
    }

    @Override
    public Page<FournisseurDto> findAll(String q, Pageable pageable) {
        return fournisseurRepository.rechercher(
                        cloisonnement.filtre(),
                        cloisonnement.filtre() ? cloisonnement.entrepriseCourante() : null,
                        RechercheUtils.normaliser(q),
                        pageable)
                .map(FournisseurDto::fromEntity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if(id==null){
            log.error("Fournisseur Id is null");
             return;
        }fournisseurRepository.delete(fournisseur(id));

    }
}
