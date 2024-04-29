package com.jumpy.tech.gestionstock.gestiondestock.validator;

import com.jumpy.tech.gestionstock.gestiondestock.dto.UserDto;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class UserValidator {
    public static List<String> validate(UserDto dto){
        List<String> errors=new ArrayList<>();
        if(dto==null){
            errors.add("Veuillez renseigner le nom de l'utilisateur");
            errors.add("Veuillez renseigner le prenom de l'utilsateur");
            errors.add("Veuillez renseigner le mot de passe de l'utilisateur");
            errors.add("Veuillez renseigner le numero de telephone ");
            errors.add("Veuillez renseingner l'adresse 1");
            errors.add("Veuillez renseigner le pays");
            errors.add("Veuillez renseigner la ville ");
            return  errors;
        }

        if(!StringUtils.hasLength(dto.getNom())){
            errors.add("Veuillez renseigner le nom de l'utilisateur");
        }
        if (!StringUtils.hasLength(dto.getPrenoms())) {
            errors.add("Veuillez renseigner le prenom de l'utilsateur");
        }
        if (!StringUtils.hasLength(dto.getMotdepasse())) {
            errors.add("Veuillez renseigner le mot de passe de l'utilisateur");
        }
        if(!StringUtils.hasLength(dto.getNumTel())){
            errors.add("Veuillez renseigner le numero de telephone ");
        }
        if(dto.getDateNaissance()== null){
            errors.add("Veuillez renseigner l'adresse de l'utilisateur");
        }
        if(dto.getAdresse()==null){
            errors.add("Veuillez renseigner l'adresse de l'utilisateur");

        } else if (!StringUtils.hasLength(dto.getAdresse().getAdresse1())) {
            errors.add("Veuillez renseingner l'adresse 1");
        }
        if(!StringUtils.hasLength(dto.getAdresse().getPays())){
            errors.add("Veuillez renseigner le pays");
        }
        if(!StringUtils.hasLength(dto.getAdresse().getVille())){
            errors.add("Veuillez renseigner la ville ");
        }
        return errors;
    }
}
