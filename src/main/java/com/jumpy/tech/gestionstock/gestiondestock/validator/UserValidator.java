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
            errors.add("Veuillez renseigner le prénom de l'utilisateur");
            errors.add("Veuillez renseigner le mot de passe de l'utilisateur");
            errors.add("Veuillez renseigner le numéro de téléphone");
            errors.add("Veuillez renseigner l'adresse de l'utilisateur");
            errors.add("Veuillez renseigner le pays");
            errors.add("Veuillez renseigner la ville");
            return  errors;
        }

        if(!StringUtils.hasLength(dto.getNom())){
            errors.add("Veuillez renseigner le nom de l'utilisateur");
        }
        if (!StringUtils.hasLength(dto.getPrenoms())) {
            errors.add("Veuillez renseigner le prénom de l'utilisateur");
        }
        if (!StringUtils.hasLength(dto.getMotdepasse())) {
            errors.add("Veuillez renseigner le mot de passe de l'utilisateur");
        }
        if(!StringUtils.hasLength(dto.getNumTel())){
            errors.add("Veuillez renseigner le numéro de téléphone");
        }
        if(dto.getDateNaissance()== null){
            // Ce controle portait sur la date de naissance mais reclamait l'adresse : celui qui
            // corrigeait son envoi ajoutait une adresse deja presente, et l'erreur revenait.
            errors.add("Veuillez renseigner la date de naissance de l'utilisateur");
        }

        // Les trois controles d'adresse etaient au meme niveau que le test de nullite : un
        // utilisateur sans adresse relevait le premier, puis la ligne suivante appelait
        // `dto.getAdresse().getPays()` sur ce meme null — une NullPointerException, donc une
        // erreur 500 la ou l'on voulait justement signaler proprement l'adresse manquante.
        if(dto.getAdresse()==null){
            errors.add("Veuillez renseigner l'adresse de l'utilisateur");
        } else {
            if (!StringUtils.hasLength(dto.getAdresse().getAdresse1())) {
                errors.add("Veuillez renseigner la première ligne de l'adresse");
            }
            if(!StringUtils.hasLength(dto.getAdresse().getPays())){
                errors.add("Veuillez renseigner le pays");
            }
            if(!StringUtils.hasLength(dto.getAdresse().getVille())){
                errors.add("Veuillez renseigner la ville");
            }
        }
        return errors;
    }
}
