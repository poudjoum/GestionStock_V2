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

    /**
     * Ce qu'il faut pour ouvrir un compte a quelqu'un qu'on ne connait pas.
     *
     * `validate` sert quand l'administrateur d'un magasin inscrit son propre personnel : il en
     * connait la date de naissance et l'adresse, et les lui demander a du sens. L'editeur qui
     * ouvre un espace a une quincaillerie n'en sait rien, et n'a aucune raison de l'apprendre.
     *
     * Restent les quatre choses sans lesquelles le compte n'existe pas : un nom pour le designer,
     * un identifiant et un mot de passe pour entrer, une adresse ou recevoir les deux. Le gerant
     * completera le reste lui-meme, ou ne le completera pas.
     */
    public static List<String> validerPourInscription(UserDto dto){
        List<String> errors=new ArrayList<>();
        if(dto==null){
            errors.add("Veuillez renseigner le gérant");
            return errors;
        }
        if(!StringUtils.hasLength(dto.getNom())){
            errors.add("Veuillez renseigner le nom du gérant");
        }
        if(!StringUtils.hasLength(dto.getUsername())){
            errors.add("Veuillez renseigner l'identifiant de connexion du gérant");
        }
        if(!StringUtils.hasLength(dto.getEmail())){
            errors.add("Veuillez renseigner l'adresse de courriel du gérant");
        }
        if(!StringUtils.hasLength(dto.getMotdepasse())){
            errors.add("Veuillez renseigner le mot de passe provisoire du gérant");
        }
        return errors;
    }
}
