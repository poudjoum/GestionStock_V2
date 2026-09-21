package com.jumpy.tech.gestionstock.gestiondestock.validator;

import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCommandeClientDto;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class LigneCmndeClientValidator {

    public static List<String> validate(LigneCommandeClientDto dto){
        List<String> errors=new ArrayList<>();

        if(dto==null){
            errors.add("Veuillez renseigner le code de la commande");
            errors.add("Veuillez sélectionner le client");
            errors.add("Veuillez renseigner une date");
            return  errors;
        }
        // getCmndeClient() peut etre null : une ligne arrivant sans sa commande faisait ici une
        // NullPointerException, donc une erreur 500, au lieu du message de validation attendu.
        if(dto.getCmndeClient() == null || !StringUtils.hasLength(dto.getCmndeClient().getCode())){
            errors.add("Veuillez renseigner le code de la commande");
        }

        return errors;
    }
}
