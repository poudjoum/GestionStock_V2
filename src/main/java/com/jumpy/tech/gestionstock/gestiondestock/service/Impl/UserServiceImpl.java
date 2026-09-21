package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.dto.UserDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Utilisateur;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.UtilisateurRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.UserService;
import com.jumpy.tech.gestionstock.gestiondestock.validator.UserValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserServiceImpl implements UserService {
    private UtilisateurRepository userRepository;
    public UserServiceImpl(UtilisateurRepository userRepository){
        this.userRepository=userRepository;
    }
    @Override
    @Transactional
    public UserDto save(UserDto dto) {
        List<String> errors= UserValidator.validate(dto);
        if(!errors.isEmpty()){
            log.error("User not Valid");
            throw new InvalidEntityException("L'utilisateur n'est pas valide", ErrorCodes.UTILISATEUR_NOT_VALID, errors);
        }
        Utilisateur savedUser=userRepository.save(UserDto.toEntity(dto));


        return UserDto.fromEntity(savedUser);
    }

    @Override
    public UserDto findById(Long id) {
        if(id==null){
            log.error("user id is null");
            throw new InvalidEntityException("Aucun utilisateur ne peut être cherché sans identifiant",
                    ErrorCodes.UTILISATEUR_NOT_VALID);
        }
        return userRepository.findById(id)
                .map(UserDto::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun utilisateur avec l'identifiant " + id + " n'a été trouvé",
                        ErrorCodes.UTILISATEUR_NOT_FOUND));
    }

    @Override
    public UserDto findUserByEmail(String email) {
        if(!StringUtils.hasLength(email)){
            log.error("L'email est vide");
            throw new InvalidEntityException("Aucun utilisateur ne peut être cherché sans adresse de courriel",
                    ErrorCodes.UTILISATEUR_NOT_VALID);
        }
        return userRepository.findUtilisateurByEmail(email)
                .map(UserDto::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun utilisateur avec l'adresse " + email + " n'a été trouvé",
                        ErrorCodes.UTILISATEUR_NOT_FOUND));
    }

    @Override
    public List<UserDto> findAll() {
        return userRepository.findAll().stream()
                .map(UserDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if(id==null){
            log.error("Article Id est null");
            return;
        }
        userRepository.deleteById(id);
    }

}
